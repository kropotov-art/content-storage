package ru.kropotov.storage.domain.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileMeta {
    private String ownerId;
    private String fileName;
    private String contentType;
    private Visibility visibility;
    private List<String> tags;

}