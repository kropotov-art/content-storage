package ru.kropotov.storage.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.FileState;
import ru.kropotov.storage.domain.model.Visibility;

import java.util.List;

public interface FileRepository extends MongoRepository<File, String> {

    Page<File> findByOwnerIdAndState(String ownerId, FileState state, Pageable pageable);

    Page<File> findByVisibilityAndState(Visibility visibility, FileState state, Pageable pageable);

    @Query("{'ownerId': ?0, 'state': ?1, 'tags': {$in: ?2}}")
    Page<File> findByOwnerIdAndStateAndTagsIn(String ownerId, FileState state, List<String> tags, Pageable pageable);

    @Query("{'visibility': ?0, 'state': ?1, 'tags': {$in: ?2}}")
    Page<File> findByVisibilityAndStateAndTagsIn(Visibility visibility, FileState state, List<String> tags, Pageable pageable);

    List<File> findByStateAndUploadTsBefore(FileState state, java.time.Instant cutoff);

}