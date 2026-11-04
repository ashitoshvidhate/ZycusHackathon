package com.stockpulse.service;

public interface ReorderStrategy {
    ReorderResult suggestReorder(RecommendationContext context);
    record ReorderResult(int quantity, int leadTimeDays, java.math.BigDecimal confidence, String reasoning, String strategyName, String failureReason) { }
}
