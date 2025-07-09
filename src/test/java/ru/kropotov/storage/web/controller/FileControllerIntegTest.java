package ru.kropotov.storage.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import ru.kropotov.storage.security.UserIdPrincipal;
import ru.kropotov.storage.web.dto.RenameRequest;
import ru.kropotov.storage.web.dto.UploadMetaDto;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(locations = "classpath:application-test.yaml")
class FileControllerIntegTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    void testU3_UploadWithSixTags_Returns400() throws Exception {
        byte[] content = "test content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String metaJson = "{\"fileName\":\"test.txt\",\"visibility\":\"PUBLIC\"," +
                "\"tags\":[\"tag1\",\"tag2\",\"tag3\",\"tag4\",\"tag5\",\"tag6\"]}";
        MockMultipartFile meta = new MockMultipartFile("meta", "", "application/json", metaJson.getBytes());

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .file(meta)
                        .with(getUser("user123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testU4_RenameOwnFile_Returns200() throws Exception {
        String fileName = "original.txt";
        String content = "test content";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("test"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        RenameRequest request = new RenameRequest("renamed.txt");

        mockMvc.perform(put("/api/files/{fileId}", uploadedFile.getId())
                        .with(getUser("user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("renamed.txt"));
    }

    @Test
    void testU4_RenameForeignFile_Returns403() throws Exception {
        String fileName = "foreign.txt";
        String content = "test content";
        
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("test"));

        File uploadedFile = fileFacade.upload("owner123", file, meta);

        RenameRequest request = new RenameRequest("hacked.txt");

        mockMvc.perform(put("/api/files/{fileId}", uploadedFile.getId())
                        .with(getUser("attacker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUploadFileWithValidTags_Returns201() throws Exception {
        byte[] content = "test content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String metaJson = "{\"fileName\":\"test.txt\",\"visibility\":\"PUBLIC\"," +
                "\"tags\":[\"java\",\"spring\",\"boot\",\"test\",\"api\"]}";
        MockMultipartFile meta = new MockMultipartFile("meta", "", "application/json", metaJson.getBytes());

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .file(meta)
                        .with(getUser("user123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("test.txt"))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags.length()").value(5));
    }

    @Test
    void testListUserFiles_Returns200() throws Exception {
        String fileName = "user-file.txt";
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "content".getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("personal"));

        fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/api/files")
                        .with(getUser("user123"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value(fileName));
    }

    @Test
    void testListPublicFiles_Returns200() throws Exception {
        String fileName = "public-file.txt";
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "content".getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PUBLIC, List.of("public"));

        fileFacade.upload("user123", file, meta);

        mockMvc.perform(get("/api/files/public")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value(fileName));
    }

    @Test
    void testListPublicFilesWithTagFilter_Returns200() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "java-file.txt", "text/plain", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "python-file.txt", "text/plain", "content2".getBytes());

        UploadMetaDto meta1 = new UploadMetaDto("java-file.txt", Visibility.PUBLIC, List.of("java", "spring"));
        UploadMetaDto meta2 = new UploadMetaDto("python-file.txt", Visibility.PUBLIC, List.of("python", "django"));

        fileFacade.upload("user123", file1, meta1);
        fileFacade.upload("user123", file2, meta2);

        mockMvc.perform(get("/api/files/public")
                        .param("tag", "java")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("java-file.txt"));
    }

    @Test
    void testDeleteFile_Returns204() throws Exception {
        String fileName = "delete-me.txt";
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "content".getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("temp"));

        File uploadedFile = fileFacade.upload("user123", file, meta);

        mockMvc.perform(delete("/api/files/{fileId}", uploadedFile.getId())
                        .with(getUser("user123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteForeignFile_Returns403() throws Exception {
        String fileName = "protected.txt";
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "content".getBytes());
        UploadMetaDto meta = new UploadMetaDto(fileName, Visibility.PRIVATE, List.of("protected"));

        File uploadedFile = fileFacade.upload("owner123", file, meta);

        mockMvc.perform(delete("/api/files/{fileId}", uploadedFile.getId())
                        .with(getUser("attacker456")))
                .andExpect(status().isForbidden());
    }


    @Test
    void testUploadWithEmptyFile_Returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        String metaJson = "{\"fileName\":\"empty.txt\",\"visibility\":\"PUBLIC\",\"tags\":[\"empty\"]}";
        MockMultipartFile meta = new MockMultipartFile("meta", "", "application/json", metaJson.getBytes());

        mockMvc.perform(multipart("/api/files")
                        .file(emptyFile)
                        .file(meta)
                        .with(getUser("user123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRenameFileNotFound_Returns404() throws Exception {
        RenameRequest request = new RenameRequest("new-name.txt");

        mockMvc.perform(put("/api/files/nonexistent-id")
                        .with(getUser("user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteFileNotFound_Returns404() throws Exception {
        mockMvc.perform(delete("/api/files/nonexistent-id")
                        .with(getUser("user123")))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor getUser(String userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        new UserIdPrincipal(userId),
                        null,
                        List.of()));
    }
}