package ru.kropotov.storage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.FileMeta;
import ru.kropotov.storage.domain.model.FileState;
import ru.kropotov.storage.domain.repository.FileRepository;
import ru.kropotov.storage.domain.repository.TagRepository;
import ru.kropotov.storage.expection.FileAlreadyExistsException;
import ru.kropotov.storage.expection.FileNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static ru.kropotov.storage.domain.model.Visibility.PRIVATE;
import static ru.kropotov.storage.domain.model.Visibility.PUBLIC;

/**
 * Интеграционный тест для FileService
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FileServiceIntegTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
    }

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    void testReserveId_Success() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("test.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .tags(List.of("java", "spring"))
                .build();

        File reservedFile = fileService.reserveId(fileMeta);

        assertNotNull(reservedFile.getId());
        assertEquals("user123", reservedFile.getOwnerId());
        assertEquals("test.txt", reservedFile.getFileName());
        assertNull(reservedFile.getSha256());
        assertEquals(0L, reservedFile.getSizeBytes());
        assertEquals(FileState.PENDING, reservedFile.getState());
        assertEquals(2, reservedFile.getTags().size());
        assertTrue(reservedFile.getTags().contains("java"));
        assertTrue(reservedFile.getTags().contains("spring"));
        assertNotNull(reservedFile.getDownloadSecret());
        assertNotNull(reservedFile.getObjectStoreKey());
    }

    @Test
    void testFinaliseUpload_Success() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("test.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .tags(List.of("Java"))
                .build();

        File reservedFile = fileService.reserveId(fileMeta);
        String actualSha256 = "abcdef123456789012345678901234567890abcdef123456789012345678901234";

        File finalizedFile = fileService.finaliseUpload(reservedFile.getId(), actualSha256, 1024L);

        assertEquals(reservedFile.getId(), finalizedFile.getId());
        assertEquals(actualSha256, finalizedFile.getSha256());
        assertEquals(1024L, finalizedFile.getSizeBytes());
        assertEquals(FileState.READY, finalizedFile.getState());
    }

    @Test
    void testReserveId_DuplicateFileName() {
        FileMeta fileMeta1 = FileMeta.builder()
                .ownerId("user123")
                .fileName("test.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        FileMeta fileMeta2 = FileMeta.builder()
                .ownerId("user123")
                .fileName("test.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        fileService.reserveId(fileMeta1);

        FileAlreadyExistsException exception = assertThrows(
                FileAlreadyExistsException.class,
                () -> fileService.reserveId(fileMeta2));

        assertEquals("name", exception.getDuplicateType());
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    void testFinaliseUpload_DuplicateHash() {
        String sameHash = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

        FileMeta fileMeta1 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file1.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        FileMeta fileMeta2 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file2.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File reservedFile1 = fileService.reserveId(fileMeta1);
        File reservedFile2 = fileService.reserveId(fileMeta2);

        fileService.finaliseUpload(reservedFile1.getId(), sameHash, 1024L);

        FileAlreadyExistsException exception = assertThrows(
                FileAlreadyExistsException.class,
                () -> fileService.finaliseUpload(reservedFile2.getId(), sameHash, 1024L));

        assertEquals("content", exception.getDuplicateType());
        assertTrue(exception.getMessage().contains("identical content"));
    }

    @Test
    void testGetUserFiles_Success() {
        FileMeta fileMeta1 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file1.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .tags(List.of("java"))
                .build();

        FileMeta fileMeta2 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file2.txt")
                .contentType("text/plain")
                .visibility(PUBLIC)
                .tags(List.of("spring"))
                .build();

        File file1 = fileService.reserveId(fileMeta1);
        File file2 = fileService.reserveId(fileMeta2);

        fileService.finaliseUpload(file1.getId(), "hash1", 1024L);
        fileService.finaliseUpload(file2.getId(), "hash2", 2048L);

        Page<File> userFiles = fileService.getUserFiles("user123", Optional.empty(), 
                PageRequest.of(0, 10));

        assertEquals(2, userFiles.getContent().size());
        assertTrue(userFiles.getContent().stream().allMatch(f -> f.getState() == FileState.READY));
    }

    @Test
    void testGetUserFiles_WithTagFilter() {
        FileMeta fileMeta1 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file1.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .tags(List.of("java"))
                .build();

        FileMeta fileMeta2 = FileMeta.builder()
                .ownerId("user123")
                .fileName("file2.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .tags(List.of("java"))
                .build();

        File file1 = fileService.reserveId(fileMeta1);
        File file2 = fileService.reserveId(fileMeta2);

        fileService.finaliseUpload(file1.getId(), "hash1", 1024L);
        fileService.finaliseUpload(file2.getId(), "hash2", 2048L);

        Page<File> javaFiles = fileService.getUserFiles("user123", Optional.of("java"), 
                PageRequest.of(0, 10));

        assertEquals(2, javaFiles.getContent().size());
        assertEquals("file1.txt", javaFiles.getContent().get(0).getFileName());
    }

    @Test
    void testRenameFile_Success() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("old-name.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        File renamedFile = fileService.renameFile(file.getId(), "user123", "new-name.txt");

        assertEquals("new-name.txt", renamedFile.getFileName());
    }

    @Test
    void testRenameFile_FileNotFound() {
        assertThrows(FileNotFoundException.class,
                () -> fileService.renameFile("nonexistent", "user123", "new-name.txt"));
    }

    @Test
    void testMarkForDeletion_Success() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("delete-me.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        File markedFile = fileService.markForDeletion(file.getId(), "user123");

        assertEquals(FileState.DELETING, markedFile.getState());
    }
}