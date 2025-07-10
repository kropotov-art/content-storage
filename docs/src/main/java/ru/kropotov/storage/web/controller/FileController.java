package ru.kropotov.storage.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.FileState;
import ru.kropotov.storage.facade.FileFacade;
import ru.kropotov.storage.web.dto.FileDto;
import ru.kropotov.storage.web.dto.RenameRequest;
import ru.kropotov.storage.web.dto.UploadMetaDto;
import ru.kropotov.storage.web.dto.request.UploadRequest;
import ru.kropotov.storage.web.mapper.FileMapper;
import ru.kropotov.storage.web.validation.NonEmptyFile;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@Validated
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Files", description = "File management operations")
public class FileController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "fileName", "uploadTs", "contentType", "sizeBytes", "tags");

    private final FileFacade fileFacade;
    private final FileMapper fileMapper;

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Upload file",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UploadRequest.class),
                            encoding = {
                                    @Encoding(name = "meta", contentType = "application/json")
                            }))
    )
    public ResponseEntity<FileDto> upload(
            @AuthenticationPrincipal(expression = "name") String userId,
            @NonEmptyFile @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("meta") UploadMetaDto meta) throws IOException {

        File savedFile = fileFacade.upload(userId, file, meta);

        if (savedFile.getState() != FileState.READY) {
            throw new IllegalStateException("File upload not completed");
        }

        log.debug("File uploaded successfully: {}", savedFile.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(fileMapper.toDto(savedFile));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List current user's files")
    public ResponseEntity<Page<FileDto>> getUserFiles(
            @AuthenticationPrincipal(expression = "name") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String tag) {

        Pageable pageable = createPageable(page, size, sort);

        Page<File> files = fileFacade.listOwn(userId, Optional.ofNullable(tag), pageable);
        Page<FileDto> fileDtos = files.map(fileMapper::toDto);

        return ResponseEntity.ok(fileDtos);
    }

    @GetMapping("/public")
    @Operation(summary = "Get public files", description = "Get paginated list of public files")
    public ResponseEntity<Page<FileDto>> getPublicFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String tag) {

        Pageable pageable = createPageable(page, size, sort);
        Page<File> files = fileFacade.listPublic(Optional.ofNullable(tag), pageable);
        Page<FileDto> fileDtos = files.map(fileMapper::toDto);

        return ResponseEntity.ok(fileDtos);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename file", description = "Rename a file")
    public ResponseEntity<FileDto> renameFile(
            @AuthenticationPrincipal(expression = "name") String userId,
            @PathVariable String id,
            @Valid @RequestBody RenameRequest request) {

        File renamedFile = fileFacade.rename(id, userId, request.getNewName());
        FileDto fileDto = fileMapper.toDto(renamedFile);

        log.info("File renamed: {} -> {}", id, request.getNewName());
        return ResponseEntity.ok(fileDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete file", description = "Delete a file and its content")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal(expression = "name") String userId,
            @PathVariable String id) {

        fileFacade.delete(id, userId);

        log.info("File deleted: {}", id);
        return ResponseEntity.noContent().build();
    }

    private Pageable createPageable(int page, int size, String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return PageRequest.of(page, size);
        }

        String[] sortParts = sort.split(",");
        String field = sortParts[0].trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1].trim())) {
            direction = Sort.Direction.DESC;
        }

        return PageRequest.of(page, size, Sort.by(direction, field));
    }
}