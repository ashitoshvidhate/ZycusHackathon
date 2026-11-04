package com.stockpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_snapshots")
public class InventorySnapshot {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "product_id", nullable = false, length = 40) private String productId;
    @Column(name = "stock_level", nullable = false) private int stockLevel;
    @Column(name = "demand_velocity", nullable = false, precision = 12, scale = 2) private BigDecimal demandVelocity;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
    @Column(nullable = false, length = 80) private String source;

    protected InventorySnapshot() { }
    public InventorySnapshot(String productId, int stockLevel, BigDecimal demandVelocity, String source) {
        this.id = UUID.randomUUID().toString(); this.productId = productId;
        this.stockLevel = stockLevel; this.demandVelocity = demandVelocity;
        this.recordedAt = Instant.now(); this.source = source;
    }
}
