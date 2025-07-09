package ru.kropotov.storage.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("storage")
public class StorageProperties {
    
    /**
     * Maximum number of tags per file
     */
    private int maxTags = 5;
    
    /**
     * Number of hours after which PENDING/FAILED files are deleted
     */
    private int cleanupHours = 4;
    
    /**
     * Interval at which the janitor job runs
     */
    private Duration cleanupInterval = Duration.ofHours(1);
    
    /**
     * Batch size for janitor cleanup
     */
    private int janitorBatchSize = 1000;
}