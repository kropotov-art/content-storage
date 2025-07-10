package ru.kropotov.storage.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.kropotov.storage.config.properties.StorageProperties;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.domain.model.FileState;
import ru.kropotov.storage.domain.repository.FileRepository;
import ru.kropotov.storage.infra.ObjectStoreClient;
import ru.kropotov.storage.service.FileService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileJanitorJob {

    private final ObjectStoreClient objectStoreClient;
    private final StorageProperties storageProperties;
    private final FileRepository fileRepository;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    private final FileService fileService;

    /**
     * Janitor job – cleans up outdated PENDING and FAILED documents in batches
     */
    @Scheduled(fixedDelayString = "#{@storageProperties.cleanupInterval.toMillis()}")
    public void janitorCleanup() {
        Instant cutoff = Instant.now().minus(storageProperties.getCleanupHours(), ChronoUnit.HOURS);

        log.info("Starting janitor cleanup for files older than: {}", cutoff);

        int totalProcessed = 0;
        int batchCount = 0;

        while (true) {
            Query findQuery = new Query(Criteria.where("state").in(FileState.PENDING, FileState.FAILED)
                    .and("uploadTs").lt(cutoff))
                    .limit(storageProperties.getJanitorBatchSize());

            List<File> staleBatch = mongoTemplate.find(findQuery, File.class);

            if (staleBatch.isEmpty()) {
                break;
            }

            batchCount++;
            List<String> fileIds = staleBatch.stream().map(File::getId).toList();

            Query markQuery = new Query(Criteria.where("id").in(fileIds)
                    .and("state").in(FileState.PENDING, FileState.FAILED));
            Update markUpdate = new Update().set("state", FileState.JANITOR);

            int markedCount = (int) mongoTemplate.updateMulti(markQuery, markUpdate, File.class).getModifiedCount();

            if (markedCount == 0) {
                continue;
            }

            log.debug("Janitor batch {}: marked {} files for cleanup", batchCount, markedCount);

            for (File file : staleBatch) {
                try {
                    objectStoreClient.delete(file.getObjectStoreKey());

                    fileRepository.deleteById(file.getId());

                    totalProcessed++;

                } catch (Exception e) {
                    log.warn("Janitor failed to clean up file: {} ({})", file.getId(), file.getState(), e);
                    fileService.updateState(file.getId(), FileState.FAILED);
                }
            }

            if (batchCount >= 100) {
                log.warn("Janitor processed maximum number of batches ({}), stopping current run", batchCount);
                break;
            }
        }

        log.info("Janitor cleanup completed: {} files processed in {} batches", totalProcessed, batchCount);
    }
}