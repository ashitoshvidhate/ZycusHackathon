package com.stockpulse.service;

import com.stockpulse.domain.Suggestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedStrategy implements PricingStrategy, ReorderStrategy {
    private final BigDecimal spikeMultiplier;
    public RuleBasedStrategy(@Value("${stockpulse.demand-spike-multiplier:2.0}") BigDecimal spikeMultiplier) { this.spikeMultiplier = spikeMultiplier; }

    @Override public PricingResult suggestPrice(RecommendationContext c) {
        var p = c.product(); var price = p.getCurrentPrice(); var direction = Suggestion.Direction.HOLD;
        if (c.trigger() == Suggestion.Trigger.INVENTORY_LOW || (c.trigger() == Suggestion.Trigger.MANUAL && p.getStockLevel() < p.getReorderThreshold())) {
            price = price.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP); direction = Suggestion.Direction.INCREASE;
        } else if (c.trigger() == Suggestion.Trigger.DEMAND_SPIKE || (c.trigger() == Suggestion.Trigger.MANUAL && isSpike(c))) {
            price = price.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_UP); direction = Suggestion.Direction.INCREASE;
        }
        return new PricingResult(price, direction, new BigDecimal("0.780"), "Rule based on " + c.trigger().name().toLowerCase().replace('_',' ') + "; category demand average " + (c.categoryAverage()==null?"unavailable":c.categoryAverage().toPlainString()) + ".", "RULE_BASED", null);
    }
    @Override public ReorderResult suggestReorder(RecommendationContext c) {
        int qty = Math.max(1, Math.subtractExact(Math.multiplyExact(c.product().getReorderThreshold(), 3), c.product().getStockLevel()));
        return new ReorderResult(qty, 7, new BigDecimal("0.780"), "Target stock is three times the reorder threshold, less current stock.", "RULE_BASED", null);
    }
    public boolean isSpike(RecommendationContext c) { return c.categoryAverage()!=null && c.categoryAverage().signum()>0 && c.product().getDemandVelocity().compareTo(c.categoryAverage().multiply(spikeMultiplier))>0; }
}
