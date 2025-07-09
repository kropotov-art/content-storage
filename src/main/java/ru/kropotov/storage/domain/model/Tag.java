package ru.kropotov.storage.domain.model;

import lombok.*;
import org.apache.commons.lang3.StringUtils;
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
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("tag cannot be null or empty");
        }
        this.name = name.toLowerCase();
    }
}