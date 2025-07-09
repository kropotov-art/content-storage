package ru.kropotov.storage.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.Visibility;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
@Testcontainers
class FileRepositoryIngetTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
    }

    @Autowired
    private FileRepository fileRepository;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
    }

    @Test
    void testParallelInsertSameFileName_OnlyOneDocumentSaved() throws InterruptedException {
        String ownerId = "user123";
        String fileName = "test-file.txt";
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<Boolean> future = executor.submit(() -> {
                try {
                    File file = createTestFile(ownerId, fileName, "sha256_" + threadId);
                    fileRepository.save(file);
                    return true;
                } catch (DuplicateKeyException e) {
                    return false;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = futures.stream()
                .mapToLong(future -> {
                    try {
                        return future.get() ? 1 : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

        assertEquals(1, successCount, "Only one file should be successfully inserted");

        List<File> files = fileRepository.findAll();
        assertEquals(1, files.size(), "Database should contain exactly one file");
    }

    @Test
    void testParallelInsertSameHash_AllDocumentSaved() throws InterruptedException {
        String ownerId = "user123";
        String sha256 = "abc123def456";
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<Boolean> future = executor.submit(() -> {
                try {
                    File file = createTestFile(ownerId, "file_" + threadId + ".txt", sha256);
                    fileRepository.save(file);
                    return true;
                } catch (DuplicateKeyException e) {
                    return false;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = futures.stream()
                .mapToLong(future -> {
                    try {
                        return future.get() ? 1 : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

        assertEquals(threadCount, successCount, "Only one file should be successfully inserted");

        List<File> files = fileRepository.findAll();
        assertEquals(threadCount, files.size(), "Database should contain exactly one file");
        assertEquals(sha256, files.get(0).getSha256());
    }

    @Test
    void testDifferentOwners_CanHaveSameFileName() {
        String fileName = "shared-name.txt";

        File file1 = createTestFile("user1", fileName, "hash1");
        File file2 = createTestFile("user2", fileName, "hash2");

        fileRepository.save(file1);
        fileRepository.save(file2);

        List<File> files = fileRepository.findAll();
        assertEquals(2, files.size(), "Both files should be saved for different owners");
    }

    private File createTestFile(String ownerId, String fileName, String sha256) {
        File file = new File();
        file.setOwnerId(ownerId);
        file.setFileName(fileName);
        file.setContentType("text/plain");
        file.setSizeBytes(1024L);
        file.setSha256(sha256);
        file.setVisibility(Visibility.PRIVATE);
        file.setUploadTs(Instant.now());
        file.setDownloadSecret(UUID.randomUUID().toString());
        file.setObjectStoreKey("object-key-" + System.nanoTime());

        return file;
    }
}