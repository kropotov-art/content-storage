package ru.kropotov.storage.infra;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.kropotov.storage.infra.dto.UploadResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;


@Slf4j
@Component
public class MinioObjectStoreClient implements ObjectStoreClient {

    private final S3Client s3Client;
    private final String bucketName;
    
    public MinioObjectStoreClient(S3Client s3Client) {
        this.s3Client = s3Client;
        this.bucketName = System.getenv().getOrDefault("MINIO_BUCKET", "default-bucket");
    }
    
    @PostConstruct
    public void init() {
        ensureBucketExists();
    }
    
    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            log.info("Bucket '{}' already exists", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("Creating bucket '{}'", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            log.info("Bucket '{}' created successfully", bucketName);
        }
    }

    @Override
    public UploadResult upload(InputStream inputStream, long sizeBytes, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);

            String key = generateKey();
            uploadWithKey(digestInputStream, sizeBytes, contentType, key);

            byte[] hashBytes = digest.digest();
            String sha256 = bytesToHex(hashBytes);

            log.info("Successfully uploaded object with key: {}, size: {}, sha256: {}",
                    key, sizeBytes, sha256);

            return new UploadResult(key, sha256, sizeBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object", e);
        }
    }

    @Override
    public void uploadWithKey(InputStream inputStream, long sizeBytes, String contentType, String key) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(sizeBytes)
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(inputStream, sizeBytes);
            s3Client.putObject(putObjectRequest, requestBody);

            log.info("Successfully uploaded object with key: {}", key);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object with key: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest);

        } catch (Exception e) {
            throw new RuntimeException("Failed to download object with key: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted object with key: {}", key);

        } catch (Exception e) {
            log.warn("Failed to delete object with key: {}", key, e);
        }
    }

    private String generateKey() {
        return "object-" + UUID.randomUUID() + "-" + System.nanoTime();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}