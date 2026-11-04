package com.stockpulse.service;

import com.stockpulse.domain.Suggestion;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class LiteLlmStrategy implements PricingStrategy, ReorderStrategy {
    private final LiteLlmGateway gateway;
    public LiteLlmStrategy(LiteLlmGateway gateway) { this.gateway=gateway; }
    @Override public PricingResult suggestPrice(RecommendationContext c) {
        var p=c.product();
        String triggerInstruction=switch(c.trigger()) {
            case INVENTORY_LOW -> "Inventory-low task: stock is below the reorder threshold. Recommend a 10% increase; do not change inventory. Return INCREASE.";
            case DEMAND_SPIKE -> "Demand-spike task: compare demand velocity to twice the category average. Recommend a 5% increase only when it exceeds that level; otherwise keep the current price and return HOLD.";
            default -> "Manual task: apply the low-inventory 10% rule first, then the demand-spike 5% rule above 2x category average; otherwise keep the current price and return HOLD.";
        };
        String prompt="Return only JSON {recommendedPrice:number,direction:INCREASE|DECREASE|HOLD,confidence:0..1,reasoning:string}. "+triggerInstruction+" Product="+p.getName()+" SKU="+p.getSku()+" category="+p.getCategory()+" currentPrice="+p.getCurrentPrice()+" stock="+p.getStockLevel()+" reorderThreshold="+p.getReorderThreshold()+" demandVelocity="+p.getDemandVelocity()+" categoryAverage="+c.categoryAverage()+". Keep price positive and within 50%-150% of current price; human approval is required.";
        var json=gateway.complete(prompt); BigDecimal price=decimal(json,"recommendedPrice"); BigDecimal confidence=decimal(json,"confidence");
        price=price.setScale(2,java.math.RoundingMode.HALF_UP);
        if(price.signum()<=0 || price.compareTo(p.getCurrentPrice().multiply(new BigDecimal("0.50")))<0 || price.compareTo(p.getCurrentPrice().multiply(new BigDecimal("1.50")))>0) throw new IllegalArgumentException("LLM price outside allowed bounds");
        if(confidence.compareTo(BigDecimal.ZERO)<0 || confidence.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("LLM confidence outside 0..1");
        Suggestion.Direction direction=Suggestion.Direction.valueOf(json.path("direction").asText());
        int comparison=price.compareTo(p.getCurrentPrice());
        if((direction==Suggestion.Direction.HOLD&&comparison!=0)||(direction==Suggestion.Direction.INCREASE&&comparison<=0)||(direction==Suggestion.Direction.DECREASE&&comparison>=0)) throw new IllegalArgumentException("LLM direction does not match recommended price");
        String reason=reason(json); return new PricingResult(price,direction,confidence,reason,"LITELLM",null);
    }
    @Override public ReorderResult suggestReorder(RecommendationContext c) {
        var p=c.product();
        String prompt="Return only JSON {recommendedQuantity:positive integer,leadTimeDays:positive integer,confidence:0..1,reasoning:string}. Suggest a supplier reorder for product="+p.getName()+" SKU="+p.getSku()+" category="+p.getCategory()+" stock="+p.getStockLevel()+" reorderThreshold="+p.getReorderThreshold()+" demandVelocity="+p.getDemandVelocity()+" trigger="+c.trigger()+". Target stock near three times threshold, quantities should be <=100000, lead time <=365 days. This suggestion requires human approval.";
        var json=gateway.complete(prompt);
        if(!json.path("recommendedQuantity").isIntegralNumber()||!json.path("leadTimeDays").isIntegralNumber()) throw new IllegalArgumentException("LLM reorder fields must be whole numbers");
        int qty=json.path("recommendedQuantity").asInt(-1), days=json.path("leadTimeDays").asInt(-1); BigDecimal confidence=decimal(json,"confidence");
        if(qty<1 || qty>100000 || days<1 || days>365) throw new IllegalArgumentException("LLM reorder values outside allowed bounds");
        if(confidence.compareTo(BigDecimal.ZERO)<0 || confidence.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("LLM confidence outside 0..1");
        return new ReorderResult(qty,days,confidence,reason(json),"LITELLM",null);
    }
    private BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode n,String field) { if(!n.has(field)||!n.get(field).isNumber()) throw new IllegalArgumentException("Invalid LLM field: "+field); return n.get(field).decimalValue(); }
    private String reason(com.fasterxml.jackson.databind.JsonNode n) { String r=n.path("reasoning").asText("").trim(); if(r.isBlank()) throw new IllegalArgumentException("LLM reasoning is required"); return r.substring(0,Math.min(1900,r.length())); }
}
