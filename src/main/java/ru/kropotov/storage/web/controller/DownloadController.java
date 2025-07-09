package ru.kropotov.storage.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.facade.FileFacade;
import ru.kropotov.storage.infra.ObjectStoreClient;

import java.io.InputStream;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Download", description = "File download operations")
public class DownloadController {


    private final FileFacade fileFacade;
    private final ObjectStoreClient objectStoreClient;
    
    @GetMapping("/d/{id}/{secret}")
    @Operation(summary = "Download file", description = "Download file by ID and secret")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable String id,
            @PathVariable String secret) {

        File file = fileFacade.getFileForDownload(id, secret);

        InputStream inputStream = objectStoreClient.download(file.getObjectStoreKey());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                   "attachment; filename=\"" + file.getFileName() + "\"");
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSizeBytes()));

        MediaType mediaType = MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        
        log.info("Downloading file: {} ({})", file.getFileName(), file.getId());
        
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(new InputStreamResource(inputStream));
    }
}