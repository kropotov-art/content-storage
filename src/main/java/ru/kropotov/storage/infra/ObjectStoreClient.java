package ru.kropotov.storage.infra;

import ru.kropotov.storage.infra.dto.UploadResult;

import java.io.InputStream;

public interface ObjectStoreClient {

    /**
     * Uploads a file to the storage
     * @param inputStream the data stream
     * @param sizeBytes the file size in bytes
     * @param contentType the MIME type of the content
     * @return upload result containing the key, SHA-256 hash, and size
     */
    UploadResult upload(InputStream inputStream, long sizeBytes, String contentType);

    /**
     * Uploads a file to the storage
     * @param inputStream the data stream
     * @param sizeBytes the file size in bytes
     * @param contentType the MIME type of the content
     * @param key the SHA-256 key
     */
    void uploadWithKey(InputStream inputStream, long sizeBytes, String contentType, String key);

    /**
     * Downloads a file from storage
     * @param key the file key
     * @return the file data stream
     */
    InputStream download(String key);

    /**
     * Deletes a file from storage
     * @param key the file key
     */
    void delete(String key);


}