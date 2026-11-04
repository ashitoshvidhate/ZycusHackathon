package com.stockpulse.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() { }
    public record StockRequest(@NotNull @Min(0) Integer stockLevel) { }
    public record OrderRequest(@NotNull @Positive Integer quantity) { }
    public record ProductCreateRequest(@NotBlank @Size(max=80) String sku,@NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=40) String category,@NotNull @DecimalMin(value="0.01") BigDecimal currentPrice,
            @DecimalMin(value="0.00") BigDecimal costPrice,@Size(max=80) String supplierId,@NotNull @Min(0) Integer stockLevel,
            @NotNull @Min(0) Integer reorderThreshold,@NotNull @DecimalMin(value="0.00") BigDecimal demandVelocity) { }
    public record ProductView(String id,String sku,String name,String category,BigDecimal currentPrice,BigDecimal costPrice,String supplierId,int stockLevel,int reorderThreshold,BigDecimal demandVelocity,String status) { }
    public record SuggestionView(UUID id,String productId,String type,String triggerReason,String status,BigDecimal recommendedPrice,Integer recommendedQuantity,BigDecimal confidence,String reasoning,String strategyName,Instant createdAt,String reviewedBy) { }
    public record StrategyRequest(@NotBlank String name) { }
    public record Envelope<T>(T data) { }
}
