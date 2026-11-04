package com.stockpulse.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_suggestions", indexes = @Index(name = "idx_ps_product_status", columnList = "product_id,status"))
public class PricingSuggestion {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "product_id", nullable = false, length = 40) private String productId;
    @Column(name = "trigger_reason", nullable = false, length = 40) private String triggerReason;
    @Column(name = "current_price", nullable = false, precision = 12, scale = 2) private BigDecimal currentPrice;
    @Column(name = "recommended_price", nullable = false, precision = 12, scale = 2) private BigDecimal recommendedPrice;
    @Column(nullable = false, length = 20) private String direction;
    @Column(nullable = false, precision = 4, scale = 3) private BigDecimal confidence;
    @Column(nullable = false, length = 2000) private String reasoning;
    @Column(name = "strategy_name", nullable = false, length = 80) private String strategyName;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "reviewed_by", length = 120) private String reviewedBy;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Version private long version;

    protected PricingSuggestion() { }
    public PricingSuggestion(String productId, Suggestion.Trigger trigger, BigDecimal currentPrice,
            BigDecimal recommendedPrice, Suggestion.Direction direction, BigDecimal confidence,
            String reasoning, String strategyName, String failureReason) {
        this.id = UUID.randomUUID().toString(); this.productId = productId; this.triggerReason = trigger.name();
        this.currentPrice = currentPrice; this.recommendedPrice = recommendedPrice; this.direction = direction.name();
        this.confidence = confidence; this.reasoning = reasoning; this.strategyName = strategyName;
        this.failureReason = failureReason; this.status = Suggestion.Status.PENDING.name(); this.createdAt = Instant.now();
    }
    public void review(boolean accept,String reviewer) {
        if (!Suggestion.Status.PENDING.name().equals(status)) throw new IllegalStateException("Only pending suggestions can be reviewed");
        status = (accept ? Suggestion.Status.ACCEPTED : Suggestion.Status.REJECTED).name(); reviewedAt = Instant.now(); reviewedBy=reviewer;
    }
    public UUID getId() { return UUID.fromString(id); }
    public String getProductId() { return productId; }
    public String getTriggerReason() { return triggerReason; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getRecommendedPrice() { return recommendedPrice; }
    public String getDirection() { return direction; }
    public BigDecimal getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public String getStrategyName() { return strategyName; }
    public String getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
