package ru.kropotov.storage.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "tags")
public class Tag {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String name;

    public Tag(String name) {
        this.name = name;
    }
}