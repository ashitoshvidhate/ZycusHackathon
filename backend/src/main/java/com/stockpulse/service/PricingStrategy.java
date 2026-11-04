package com.stockpulse.service;

import com.stockpulse.domain.Suggestion;
import java.math.BigDecimal;

public interface PricingStrategy {
    PricingResult suggestPrice(RecommendationContext context);
    record PricingResult(BigDecimal price, Suggestion.Direction direction, BigDecimal confidence, String reasoning, String strategyName, String failureReason) { }
}
