package ru.kropotov.storage.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RenameRequest {
    
    @NotBlank(message = "New name is required")
    @Size(max = 255, message = "File name must not exceed 255 characters")
    private String newName;
}