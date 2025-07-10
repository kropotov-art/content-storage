package ru.kropotov.storage.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import ru.kropotov.storage.domain.model.Visibility;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileDto {
    private String id;
    private String fileName;
    private long sizeBytes;
    private String contentType;
    private Visibility visibility;
    private List<String> tags;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant uploadTs;
    
    private String downloadUrl;
    
}
