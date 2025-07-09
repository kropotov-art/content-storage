package ru.kropotov.storage.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tag information")
public class TagDto {
    
    @Schema(description = "Tag identifier", example = "abc123")
    private String id;
    
    @Schema(description = "Tag name (canonical lowercase)", example = "invoice")
    private String name;
}