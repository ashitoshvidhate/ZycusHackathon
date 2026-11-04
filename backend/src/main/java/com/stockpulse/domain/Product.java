package com.stockpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @Column(length = 40)
    private String id;
    @Column(nullable = false, unique = true, length = 80)
    private String sku;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 40)
    private String category;
    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;
    @Column(name = "supplier_id", length = 80)
    private String supplierId;
    @Column(name = "stock_level", nullable = false)
    private int stockLevel;
    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;
    @Column(name = "demand_velocity", nullable = false, precision = 12, scale = 2)
    private BigDecimal demandVelocity;
    @Column(nullable = false, length = 40)
    private String status;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { }

    public Product(String id, String sku, String name, String category, BigDecimal price,
                   int stock, int threshold, BigDecimal velocity, String status) {
        this(id, sku, name, category, price, null, null, stock, threshold, velocity, status);
    }

    public Product(String id, String sku, String name, String category, BigDecimal price,
                   BigDecimal costPrice, String supplierId, int stock, int threshold,
                   BigDecimal velocity, String status) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.currentPrice = price;
        this.costPrice = costPrice;
        this.supplierId = supplierId;
        this.stockLevel = stock;
        this.reorderThreshold = threshold;
        this.demandVelocity = velocity;
        this.status = stock == 0 ? "OUT_OF_STOCK" : stock < threshold ? "LOW_STOCK" : status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void updateTimestamp() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void setStockLevel(int stockLevel) {
        if (stockLevel < 0) throw new IllegalArgumentException("Stock cannot be negative");
        this.stockLevel = stockLevel;
        this.status = stockLevel == 0 ? "OUT_OF_STOCK"
                : stockLevel < reorderThreshold ? "LOW_STOCK" : "ACTIVE";
    }

    public void setCurrentPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        currentPrice = price;
    }

    public void setDemandVelocity(BigDecimal velocity) {
        if (velocity == null || velocity.signum() < 0) throw new IllegalArgumentException("Demand velocity cannot be negative");
        demandVelocity = velocity;
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public String getSupplierId() { return supplierId; }
    public int getStockLevel() { return stockLevel; }
    public int getReorderThreshold() { return reorderThreshold; }
    public BigDecimal getDemandVelocity() { return demandVelocity; }
    public String getStatus() { return status; }
}
