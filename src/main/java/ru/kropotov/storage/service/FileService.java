package ru.kropotov.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.annotation.Transactional;
import ru.kropotov.storage.domain.model.*;
import ru.kropotov.storage.domain.repository.FileRepository;
import ru.kropotov.storage.expection.AccessDeniedException;
import ru.kropotov.storage.expection.FileAlreadyExistsException;
import ru.kropotov.storage.expection.FileNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.kropotov.storage.domain.model.Visibility.PRIVATE;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final TagService tagService;
    private final MongoTemplate mongoTemplate;

    @Transactional(rollbackFor = Exception.class)
    public File reserveId(FileMeta fileMeta) {
        tagService.validateAndNormalizeTags(fileMeta.getTags());

        tagService.createMissingTags(fileMeta.getTags());

        File file = File.builder()
                .ownerId(fileMeta.getOwnerId())
                .fileName(fileMeta.getFileName())
                .fileNameLower(fileMeta.getFileName().trim().toLowerCase())
                .contentType(fileMeta.getContentType())
                .sizeBytes(0L)
                .sha256("PENDING")
                .visibility(fileMeta.getVisibility())
                .tags(fileMeta.getTags())
                .uploadTs(Instant.now())
                .state(FileState.PENDING)
                .objectStoreKey(generateObjectStoreKey())
                .downloadSecret(generateDownloadSecret())
                .build();

        try {
            return fileRepository.save(file);
        } catch (DuplicateKeyException e) {
            throw handleDuplicateKeyException(e, fileMeta);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public File finaliseUpload(String fileId, String sha256, long actualSize) {
        File pendingFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalStateException("File not found: " + fileId));

        if (pendingFile.getState() != FileState.PENDING) {
            throw new IllegalStateException("File not in PENDING state: " + fileId);
        }

        Query duplicateQuery = new Query(Criteria.where("ownerId").is(pendingFile.getOwnerId())
                .and("sha256").is(sha256.toLowerCase())
                .and("state").is(FileState.READY));

        File existingFile = mongoTemplate.findOne(duplicateQuery, File.class);

        if (existingFile != null) {
            updateState(fileId, FileState.FAILED);
            throw new FileAlreadyExistsException(
                    "File with identical content already exists for this user",
                    "content");
        }

        Query query = new Query(Criteria.where("id").is(fileId).and("state").is(FileState.PENDING));
        Update update = new Update()
                .set("sha256", sha256.toLowerCase())
                .set("sizeBytes", actualSize)
                .set("state", FileState.READY);

        File result = mongoTemplate.findAndModify(query, update, File.class);

        if (result == null) {
            throw new IllegalStateException("File not found in PENDING state during finalization: " + fileId);
        }

        result.setSha256(sha256.toLowerCase());
        result.setSizeBytes(actualSize);
        result.setState(FileState.READY);

        log.info("Finalized upload: {} -> {} bytes, SHA-256: {}", fileId, actualSize, sha256);
        return result;
    }

    public void updateState(String fileId, FileState newState) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set("state", newState);
        mongoTemplate.updateFirst(query, update, File.class);
        log.debug("Updated file {} state to {}", fileId, newState);
    }

    @Transactional(rollbackFor = Exception.class)
    public File markForDeletion(String fileId, String userId) {
        Query query = new Query(Criteria.where("id").is(fileId)
                .and("ownerId").is(userId)
                .and("state").is(FileState.READY));
        Update update = new Update().set("state", FileState.DELETING);

        File result = mongoTemplate.findAndModify(query, update, File.class);

        if (result == null) {
            Optional<File> fileOpt = fileRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                throw new FileNotFoundException("File not found");
            }

            File file = fileOpt.get();
            if (!userId.equals(file.getOwnerId())) {
                throw new AccessDeniedException("You don't have permission to access this file");
            }

            throw new IllegalStateException("File not in READY state: " + fileId);
        }

        result.setState(FileState.DELETING);
        return result;
    }

    public void deleteMetadata(String fileId) {
        fileRepository.deleteById(fileId);
        log.debug("Deleted file metadata: {}", fileId);
    }

    public Page<File> getUserFiles(String userId, Optional<String> tag, Pageable pageable) {
        if (tag.isPresent() && !tag.get().trim().isEmpty()) {
            String normalizedTag = tag.get().trim().toLowerCase();
            return fileRepository.findByOwnerIdAndStateAndTagsIn(userId, FileState.READY,
                    List.of(normalizedTag), pageable);
        }
        return fileRepository.findByOwnerIdAndState(userId, FileState.READY, pageable);
    }

    public Page<File> getPublicFiles(Optional<String> tag, Pageable pageable) {
        if (tag.isPresent() && !tag.get().trim().isEmpty()) {
            String normalizedTag = tag.get().trim().toLowerCase();
            return fileRepository.findByVisibilityAndStateAndTagsIn(Visibility.PUBLIC, FileState.READY,
                    List.of(normalizedTag), pageable);
        }
        return fileRepository.findByVisibilityAndState(Visibility.PUBLIC, FileState.READY, pageable);
    }

    @Transactional(rollbackFor = Exception.class)
    public File renameFile(String fileId, String userId, String newName) {
        File file = findFileByIdAndOwner(fileId, userId);

        if (file.getState() != FileState.READY) {
            throw new IllegalStateException("Cannot rename file in state: " + file.getState());
        }

        file.setFileName(newName);
        file.setFileNameLower(newName.trim().toLowerCase());

        try {
            return fileRepository.save(file);
        } catch (DuplicateKeyException e) {
            throw new FileAlreadyExistsException(
                    String.format("File with name '%s' already exists for this user", newName),
                    "name");
        }
    }

    public File getFileForDownload(String fileId, String secret) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new FileNotFoundException("File not found");
        }

        File file = fileOpt.get();

        if (file.getState() != FileState.READY) {
            throw new FileNotFoundException("File not available");
        }

        if (!secret.equals(file.getDownloadSecret())) {
            throw new AccessDeniedException("Invalid download secret");
        }

        return file;
    }

    private File findFileByIdAndOwner(String fileId, String ownerId) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new FileNotFoundException("File not found");
        }

        File file = fileOpt.get();
        if (!ownerId.equals(file.getOwnerId())) {
            if (PRIVATE.equals(fileOpt.get().getVisibility())) {
                throw new FileNotFoundException("File not found");
            } else {
                throw new AccessDeniedException("You don't have permission to access this file");
            }
        }

        return file;
    }

    private String generateObjectStoreKey() {
        return "file-" + UUID.randomUUID() + "-" + System.nanoTime();
    }

    private String generateDownloadSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private FileAlreadyExistsException handleDuplicateKeyException(DuplicateKeyException e, FileMeta fileMeta) {
        String errorMessage = e.getMessage();
        String duplicateType;
        String message;

        if (errorMessage.contains("owner_name")) {
            duplicateType = "name";
            message = String.format("File with name '%s' already exists for this user",
                    fileMeta.getFileName());
        } else {
            duplicateType = "unknown";
            message = "File already exists";
        }

        log.warn("Duplicate file detected: {} - {}", duplicateType, message);
        return new FileAlreadyExistsException(message, duplicateType);
    }
}