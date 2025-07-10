package ru.kropotov.storage.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.kropotov.storage.domain.model.Tag;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends MongoRepository<Tag, String> {
    
    List<Tag> findByNameIn(Collection<String> nameLowers);
}