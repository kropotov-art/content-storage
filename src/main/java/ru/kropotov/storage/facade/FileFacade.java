package ru.kropotov.storage.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.FileMeta;
import ru.kropotov.storage.domain.model.FileState;
import ru.kropotov.storage.expection.AccessDeniedException;
import ru.kropotov.storage.expection.FileAlreadyExistsException;
import ru.kropotov.storage.expection.FileNotFoundException;
import ru.kropotov.storage.service.FileService;
import ru.kropotov.storage.infra.ObjectStoreClient;
import ru.kropotov.storage.web.dto.UploadMetaDto;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Application‑layer façade that encapsulates the end‑to‑end workflow around files
 * so that web/controllers do not touch domain services directly.
 * All public methods are <b>idempotent</b> and validated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileFacade {

    private final ObjectStoreClient objectStoreClient;
    private final FileService fileService;

    public File upload(String ownerId, MultipartFile multipartFile, UploadMetaDto uploadMetaDto) {
        String reservedId = null;
        String objectStoreKey = null;

        try {
            String fileName = uploadMetaDto.getFileName() != null ?
                    uploadMetaDto.getFileName() : multipartFile.getOriginalFilename();

            FileMeta fileMeta = FileMeta.builder()
                    .ownerId(ownerId)
                    .fileName(fileName)
                    .contentType(multipartFile.getContentType())
                    .visibility(uploadMetaDto.getVisibility())
                    .tags(uploadMetaDto.getTags())
                    .build();

            File reservedFile = fileService.reserveId(fileMeta);
            reservedId = reservedFile.getId();
            objectStoreKey = reservedFile.getObjectStoreKey();

            log.info("Reserved file ID: {} with key: {}", reservedId, objectStoreKey);

            UploadResult uploadResult = uploadWithSha256(multipartFile, objectStoreKey);

            File finalizedFile = fileService.finaliseUpload(
                    reservedId,
                    uploadResult.sha256,
                    uploadResult.actualSize
            );

            log.info("Successfully uploaded file: {} ({})", reservedId, finalizedFile.getFileName());
            return finalizedFile;

        } catch (FileAlreadyExistsException e) {
            log.info("Duplicate file detected during upload, cleaning up and returning existing file");

            compensateFailedUpload(reservedId, objectStoreKey);

            throw e;

        } catch (Exception e) {
            log.error("Upload failed for reserved ID: {}", reservedId, e);

            compensateFailedUpload(reservedId, objectStoreKey);

            throw new RuntimeException("Upload failed", e);
        }
    }

    public Page<File> listOwn(String userId, Optional<String> tag, Pageable pageable) {
        return fileService.getUserFiles(userId, tag, pageable);
    }

    public Page<File> listPublic(Optional<String> tag, Pageable pageable) {
        return fileService.getPublicFiles(tag, pageable);
    }

    public File rename(String fileId, String ownerId, String newName) {
        return fileService.renameFile(fileId, ownerId, newName);
    }

    public void delete(String fileId, String ownerId) {
        try {
            File file = fileService.markForDeletion(fileId, ownerId);
            objectStoreClient.delete(file.getObjectStoreKey());
            fileService.deleteMetadata(fileId);

            log.info("Successfully deleted file: {}", fileId);

        } catch (FileNotFoundException | AccessDeniedException | IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Failed to delete file: {}", fileId, e);
            try {
                fileService.updateState(fileId, FileState.READY);
            } catch (Exception rollbackException) {
                log.error("Failed to rollback file state after delete failure: {}", fileId, rollbackException);
            }
            throw new RuntimeException("Delete failed", e);
        }
    }

    public File getFileForDownload(String fileId, String secret) {
        return fileService.getFileForDownload(fileId, secret);
    }

    private UploadResult uploadWithSha256(MultipartFile multipartFile, String objectStoreKey) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (InputStream originalStream = multipartFile.getInputStream();
             DigestInputStream hashingStream = new DigestInputStream(originalStream, md)) {

            objectStoreClient.uploadWithKey(hashingStream, multipartFile.getSize(),
                    multipartFile.getContentType(), objectStoreKey);

            String sha256 = HexFormat.of().formatHex(md.digest());

            return new UploadResult(sha256, multipartFile.getSize());
        }
    }

    private void compensateFailedUpload(String reservedId, String objectStoreKey) {
        try {
            if (objectStoreKey != null) {
                objectStoreClient.delete(objectStoreKey);
            }

            if (reservedId != null) {
                fileService.updateState(reservedId, FileState.FAILED);
            }

            log.debug("Compensated failed upload: {} -> {}", reservedId, objectStoreKey);

        } catch (Exception e) {
            log.error("Compensation failed for: {} -> {}", reservedId, objectStoreKey, e);
        }
    }

    private static class UploadResult {
        final String sha256;
        final long actualSize;

        UploadResult(String sha256, long actualSize) {
            this.sha256 = sha256;
            this.actualSize = actualSize;
        }
    }
}