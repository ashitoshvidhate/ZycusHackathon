package com.stockpulse.service;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.Suggestion;
import java.math.BigDecimal;

public record RecommendationContext(Product product, Suggestion.Trigger trigger, BigDecimal categoryAverage) { }
