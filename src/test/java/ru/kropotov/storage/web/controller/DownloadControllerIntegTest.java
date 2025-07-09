package ru.kropotov.storage.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.Visibility;
import ru.kropotov.storage.domain.repository.FileRepository;
import ru.kropotov.storage.domain.repository.TagRepository;
import ru.kropotov.storage.facade.FileFacade;
import ru.kropotov.storage.web.dto.UploadMetaDto;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(locations = "classpath:application-test.yaml")
class DownloadControllerIntegTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
        registry.add("minio.endpoint", minioContainer::getS3URL);
        registry.add("minio.accessKey", minioContainer::getUserName);
        registry.add("minio.secretKey", minioContainer::getPassword);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FileFacade fileFacade;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TagRepository tagRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();
        
        fileRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    void testU9_DownloadViaDownloadUrl_Returns200WithContentDisposition() throws Exception {
        String fileName = "download-test.txt";
        String content = "This is downloadable content";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("download"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileName + "\""))
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Length", String.valueOf(content.length())))
                .andExpect(content().string(content));
    }

    @Test
    void testDownloadWithInvalidSecret_Returns403() throws Exception {
        String fileName = "secure-file.txt";
        String content = "This is secure content";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("secure"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), "invalid-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDownloadNonExistentFile_Returns404() throws Exception {
        mockMvc.perform(get("/d/{fileId}/{secret}", "nonexistent-id", "any-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadWithSpecialCharactersInFilename() throws Exception {
        String fileName = "file with spaces & symbols (2024).txt";
        String content = "Content with special filename";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("special"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileName + "\""))
                .andExpect(header().string("Content-Length", String.valueOf(content.length())))
                .andExpect(content().string(content));
    }

    @Test
    void testDownloadLargeFile_ReturnsCorrectContentLength() throws Exception {
        String fileName = "large-file.bin";
        byte[] largeContent = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", largeContent);
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("large"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileName + "\""))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Length", String.valueOf(largeContent.length)))
                .andExpect(content().bytes(largeContent));
    }

    @Test
    void testDownloadWithUTF8Filename() throws Exception {
        String fileName = "测试文件.txt";
        String content = "UTF-8 filename test";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("utf8"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileName + "\""))
                .andExpect(content().string(content));
    }

    @Test
    void testDownloadImageFile_ReturnsCorrectMimeType() throws Exception {
        String fileName = "test-image.jpg";
        byte[] jpegContent = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "image/jpeg", jpegContent);
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("image"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileName + "\""))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Length", String.valueOf(jpegContent.length)))
                .andExpect(content().bytes(jpegContent));
    }

    @Test
    void testDownloadNoAuthRequired() throws Exception {
        String fileName = "public-file.txt";
        String content = "This is public content";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("public"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        // Download should work without any authentication headers
        mockMvc.perform(get("/d/{fileId}/{secret}", uploadedFile.getId(), uploadedFile.getDownloadSecret()))
                .andExpect(status().isOk())
                .andExpect(content().string(content));
    }
}