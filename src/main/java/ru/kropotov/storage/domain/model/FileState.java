package ru.kropotov.storage.domain.model;

/**
 * File state during the upload process
 */
public enum FileState {
    PENDING,    // Reserved in MongoDB, upload to S3 not yet completed
    READY,      // Successfully uploaded to S3 and finalized in MongoDB
    FAILED,     // Upload failed, requires cleanup
    DELETING,   // Marked for deletion
    JANITOR     // Being processed by the janitor job
}