package ru.kropotov.storage.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ru.kropotov.storage.domain.model.Visibility;
import ru.kropotov.storage.web.validation.ValidTags;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadMetaDto {

    @Size(max = 255, message = "File name must not exceed 255 characters")
    private String fileName;
    
    @NotNull(message = "Visibility is required")
    private Visibility visibility;
    
    @ValidTags
    private List<String> tags;

}