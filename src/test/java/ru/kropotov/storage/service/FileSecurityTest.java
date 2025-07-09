package ru.kropotov.storage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import ru.kropotov.storage.expection.AccessDeniedException;
import ru.kropotov.storage.expection.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.kropotov.storage.domain.model.Visibility.PRIVATE;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FileSecurityTest {

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
    void testRenameFile_Owner_Success() {
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
        assertEquals("user123", renamedFile.getOwnerId());
    }

    @Test
    void testRenameFile_NotOwner_AccessDenied() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("owner123")
                .fileName("secret-file.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        assertThrows(FileNotFoundException.class,
                () -> fileService.renameFile(file.getId(), "attacker456", "hacked.txt"));
    }

    @Test
    void testMarkForDeletion_Owner_Success() {
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
        assertEquals("user123", markedFile.getOwnerId());
    }

    @Test
    void testMarkForDeletion_NotOwner_AccessDenied() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("owner123")
                .fileName("secret-file.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        assertThrows(AccessDeniedException.class,
                () -> fileService.markForDeletion(file.getId(), "attacker456"));
    }

    @Test
    void testGetFileForDownload_ValidSecret_Success() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("download-me.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        File downloadFile = fileService.getFileForDownload(file.getId(), file.getDownloadSecret());

        assertEquals(file.getId(), downloadFile.getId());
        assertEquals(file.getFileName(), downloadFile.getFileName());
        assertEquals(FileState.READY, downloadFile.getState());
    }

    @Test
    void testGetFileForDownload_InvalidSecret_AccessDenied() {
        FileMeta fileMeta = FileMeta.builder()
                .ownerId("user123")
                .fileName("secure-file.txt")
                .contentType("text/plain")
                .visibility(PRIVATE)
                .build();

        File file = fileService.reserveId(fileMeta);
        fileService.finaliseUpload(file.getId(), "hash123", 1024L);

        assertThrows(AccessDeniedException.class,
                () -> fileService.getFileForDownload(file.getId(), "wrong-secret"));
    }

    @Test
    void testGetFileForDownload_FileNotFound() {
        assertThrows(FileNotFoundException.class,
                () -> fileService.getFileForDownload("nonexistent", "any-secret"));
    }
}
