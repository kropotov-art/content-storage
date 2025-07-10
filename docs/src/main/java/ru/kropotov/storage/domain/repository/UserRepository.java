package ru.kropotov.storage.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.kropotov.storage.domain.model.User;

public interface UserRepository extends MongoRepository<User, String> {
}