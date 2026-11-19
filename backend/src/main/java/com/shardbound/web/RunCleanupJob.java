package com.shardbound.web;

import com.shardbound.config.ShardboundProperties;
import com.shardbound.persistence.ActiveRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes games in progress that nobody has touched for {@code shardbound.run-retention}. */
@Component
public class RunCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(RunCleanupJob.class);

    private final ActiveRunRepository runs;
    private final ShardboundProperties properties;

    public RunCleanupJob(ActiveRunRepository runs, ShardboundProperties properties) {
        this.runs = runs;
        this.properties = properties;
    }

    @Scheduled(cron = "${shardbound.run-cleanup-cron:0 30 3 * * *}")
    public void deleteIdleRuns() {
        int deleted = runs.deleteIdle(properties.runRetention());
        if (deleted > 0) {
            LOG.info("Deleted {} idle games in progress", deleted);
        }
    }
}
