package com.stockpulse.service;

import com.stockpulse.domain.RecommendationJob;
import com.stockpulse.domain.Suggestion;
import com.stockpulse.repo.RecommendationJobRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationJobService {
    private final RecommendationJobRepository jobs; private final RecommendationService recommendations; private final ApplicationEventPublisher events;
    public RecommendationJobService(RecommendationJobRepository jobs,RecommendationService recommendations,ApplicationEventPublisher events) { this.jobs=jobs; this.recommendations=recommendations; this.events=events; }
    /** Save the work record in the same transaction as the stock/order mutation. */
    @Transactional public String enqueue(String productId,Suggestion.Trigger trigger) {
        RecommendationJob job=jobs.save(new RecommendationJob(productId,trigger));
        events.publishEvent(new RecommendationRequested(job.getId()));
        return job.getId();
    }
    @Transactional public void process(String jobId) {
        RecommendationJob job=jobs.findForUpdate(jobId).orElseThrow(()->new NoSuchElementException("Recommendation job not found"));
        if("COMPLETED".equals(job.getStatus())) return;
        job.startAttempt();
        recommendations.createBoth(job.getProductId(),job.getTrigger());
        job.complete(); jobs.save(job);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void markFailed(String jobId,String code) {
        jobs.findForUpdate(jobId).ifPresent(job->{if(!"COMPLETED".equals(job.getStatus())) { job.fail(code); jobs.save(job); }});
    }
    @Transactional(readOnly=true) public List<String> recoverableJobIds() {
        return jobs.findTop100ByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(List.of("PENDING","FAILED"),3).stream().map(RecommendationJob::getId).toList();
    }
}
