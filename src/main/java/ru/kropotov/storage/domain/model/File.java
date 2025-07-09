package ru.kropotov.storage.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "files")
@CompoundIndexes({
    @CompoundIndex(name = "owner_name", def = "{'ownerId':1,'fileNameLower':1}", unique = true),
    @CompoundIndex(name = "owner_hash", def = "{'ownerId':1,'sha256':1}", unique = true),
    @CompoundIndex(def = "{'visibility':1}"),
    @CompoundIndex(def = "{'tags':1}"),
    @CompoundIndex(def = "{'state':1,'uploadTs':1}")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class File {
    
    @Id
    private String id;
    private String ownerId;
    private String fileName;
    private String fileNameLower;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private Visibility visibility;
    private List<String> tags;
    private Instant uploadTs;
    private String downloadSecret;
    private String objectStoreKey;
    @Indexed
    private FileState state;
    
}