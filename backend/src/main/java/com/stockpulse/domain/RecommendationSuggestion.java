package com.stockpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendation_suggestions", indexes = {
        @Index(name = "idx_suggestion_product_status", columnList = "product_id,status"),
        @Index(name = "idx_suggestion_created_at", columnList = "created_at")
})
public class RecommendationSuggestion {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "trigger_reason", nullable = false, length = 40)
    private String triggerReason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "recommended_price", precision = 12, scale = 2)
    private BigDecimal recommendedPrice;

    @Column(name = "recommended_quantity")
    private Integer recommendedQuantity;

    @Column(precision = 4, scale = 3, nullable = false)
    private BigDecimal confidence;

    @Column(nullable = false, length = 2000)
    private String reasoning;

    @Column(name = "strategy_name", nullable = false, length = 80)
    private String strategyName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    private long version;

    protected RecommendationSuggestion() { }

    public RecommendationSuggestion(String productId, String triggerReason, BigDecimal price,
                                    Integer quantity, BigDecimal confidence, String reasoning,
                                    String strategyName) {
        this.id = UUID.randomUUID().toString();
        this.productId = productId;
        this.type = "COMBINED";
        this.triggerReason = triggerReason;
        this.status = "PENDING";
        this.recommendedPrice = price;
        this.recommendedQuantity = quantity;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.strategyName = strategyName;
        this.createdAt = Instant.now();
    }

    public void review(String decision) {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException("Only pending suggestions can be reviewed");
        }
        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("Unsupported suggestion decision");
        }
        status = decision;
        reviewedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getType() { return type; }
    public String getTriggerReason() { return triggerReason; }
    public String getStatus() { return status; }
    public BigDecimal getRecommendedPrice() { return recommendedPrice; }
    public Integer getRecommendedQuantity() { return recommendedQuantity; }
    public BigDecimal getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public String getStrategyName() { return strategyName; }
}
