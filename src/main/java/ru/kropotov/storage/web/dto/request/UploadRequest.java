package ru.kropotov.storage.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;
import ru.kropotov.storage.web.dto.UploadMetaDto;

@Schema(name = "UploadRequest")
public class UploadRequest {
    @Schema(type = "string", format = "binary")
    public MultipartFile file;
    @Schema(implementation = UploadMetaDto.class)
    public UploadMetaDto meta;
}