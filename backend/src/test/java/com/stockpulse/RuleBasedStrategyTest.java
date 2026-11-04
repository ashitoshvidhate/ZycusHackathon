package com.stockpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.Suggestion;
import com.stockpulse.service.RecommendationContext;
import com.stockpulse.service.RuleBasedStrategy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RuleBasedStrategyTest {
    private final RuleBasedStrategy strategy = new RuleBasedStrategy(new BigDecimal("2.0"));

    @Test void lowInventoryRaisesPriceByTenPercentWithCurrencyRounding() {
        var result = strategy.suggestPrice(context(product("20.00", 4, 10, "0"), Suggestion.Trigger.INVENTORY_LOW, "5"));
        assertEquals(new BigDecimal("22.00"), result.price());
        assertEquals(Suggestion.Direction.INCREASE, result.direction());
    }

    @Test void demandSpikeRaisesPriceByFivePercentAndThresholdIsStrict() {
        Product highDemand = product("10.00", 50, 10, "10.01");
        assertTrue(strategy.isSpike(context(highDemand, Suggestion.Trigger.DEMAND_SPIKE, "5")));
        assertEquals(new BigDecimal("10.50"), strategy.suggestPrice(context(highDemand, Suggestion.Trigger.DEMAND_SPIKE, "5")).price());
        assertFalse(strategy.isSpike(context(product("10.00", 50, 10, "10.00"), Suggestion.Trigger.DEMAND_SPIKE, "5")));
    }

    @Test void manualPricingHoldsWhenNoRiskSignalExists() {
        var result = strategy.suggestPrice(context(product("19.99", 20, 5, "1"), Suggestion.Trigger.MANUAL, "5"));
        assertEquals(new BigDecimal("19.99"), result.price());
        assertEquals(Suggestion.Direction.HOLD, result.direction());
    }

    @Test void reorderTargetsThreeTimesThresholdAndNeverSuggestsZero() {
        assertEquals(10, strategy.suggestReorder(context(product("10.00", 20, 10, "0"), Suggestion.Trigger.MANUAL, "0")).quantity());
        assertEquals(1, strategy.suggestReorder(context(product("10.00", 40, 10, "0"), Suggestion.Trigger.MANUAL, "0")).quantity());
    }

    private RecommendationContext context(Product product, Suggestion.Trigger trigger, String categoryAverage) {
        return new RecommendationContext(product, trigger, new BigDecimal(categoryAverage));
    }

    private Product product(String price, int stock, int threshold, String velocity) {
        return new Product("unit-product", "UNIT-SKU", "Unit test product", "HOME", new BigDecimal(price),
                stock, threshold, new BigDecimal(velocity), "ACTIVE");
    }
}
