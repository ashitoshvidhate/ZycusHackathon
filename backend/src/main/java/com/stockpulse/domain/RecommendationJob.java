package com.stockpulse.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Durable marker for post-commit recommendation work; it is the recovery source if dispatch is interrupted. */
@Entity
@Table(name="recommendation_jobs",indexes=@Index(name="idx_recommendation_jobs_status_created",columnList="status,created_at"))
public class RecommendationJob {
    @Id @Column(length=36) private String id;
    @Column(name="product_id",nullable=false,length=40) private String productId;
    @Column(name="trigger_reason",nullable=false,length=40) private String triggerReason;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false) private int attempts;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="last_error_code",length=80) private String lastErrorCode;
    @Version private long version;

    protected RecommendationJob() { }
    public RecommendationJob(String productId,Suggestion.Trigger trigger) {
        this.id=UUID.randomUUID().toString(); this.productId=productId; this.triggerReason=trigger.name(); this.status="PENDING";
        this.attempts=0; this.createdAt=Instant.now(); this.updatedAt=this.createdAt;
    }
    public void startAttempt() { if("COMPLETED".equals(status)) return; status="RUNNING"; attempts++; updatedAt=Instant.now(); lastErrorCode=null; }
    public void complete() { status="COMPLETED"; completedAt=Instant.now(); updatedAt=completedAt; lastErrorCode=null; }
    public void fail(String code) { status="FAILED"; attempts++; updatedAt=Instant.now(); lastErrorCode=code; }
    public String getId(){return id;} public String getProductId(){return productId;}
    public Suggestion.Trigger getTrigger(){return Suggestion.Trigger.valueOf(triggerReason);}
    public String getStatus(){return status;} public int getAttempts(){return attempts;}
}
