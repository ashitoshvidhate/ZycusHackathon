package com.stockpulse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Processes durable recommendation jobs after commit and replays unfinished jobs at startup. */
@Component
public class RecommendationEventListener {
    private static final Logger log=LoggerFactory.getLogger(RecommendationEventListener.class);
    private final RecommendationJobService jobs;
    public RecommendationEventListener(RecommendationJobService jobs) { this.jobs=jobs; }

    @Async("recommendationExecutor") @TransactionalEventListener
    public void onRecommendationRequested(RecommendationRequested event) { processWithRetry(event.jobId()); }

    @Async("recommendationExecutor") @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart(ApplicationReadyEvent event) {
        for(String id:jobs.recoverableJobIds()) processWithRetry(id);
    }

    private void processWithRetry(String jobId) {
        for(int retry=1;retry<=3;retry++) {
            try { jobs.process(jobId); log.info("recommendation_job_completed jobId={} attempt={}",jobId,retry); return; }
            catch(RuntimeException ex) {
                jobs.markFailed(jobId,"RECOMMENDATION_GENERATION_FAILED");
                log.warn("recommendation_job_attempt_failed jobId={} attempt={} errorType={}",jobId,retry,ex.getClass().getSimpleName());
                if(retry<3) try { Thread.sleep(100L*retry); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
        log.error("recommendation_job_exhausted jobId={} status=FAILED",jobId);
    }
}
