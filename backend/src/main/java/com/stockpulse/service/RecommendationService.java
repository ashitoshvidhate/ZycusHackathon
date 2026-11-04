package com.stockpulse.service;

import com.stockpulse.api.ApiModels.SuggestionView;
import com.stockpulse.domain.*;
import com.stockpulse.repo.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class RecommendationService {
    private static final Logger log=LoggerFactory.getLogger(RecommendationService.class);
    private final ProductRepository products; private final PricingSuggestionRepository pricing; private final ReorderSuggestionRepository reorders;
    private final RuleBasedStrategy rules; private final LiteLlmStrategy llm; private final StrategySelector selector;
    public RecommendationService(ProductRepository products, PricingSuggestionRepository pricing, ReorderSuggestionRepository reorders,
            RuleBasedStrategy rules, LiteLlmStrategy llm, StrategySelector selector) {
        this.products=products; this.pricing=pricing; this.reorders=reorders; this.rules=rules; this.llm=llm; this.selector=selector;
    }

    @Transactional public SuggestionView createPricing(String productId, Suggestion.Trigger trigger) {
        // Serialize per-product creation so concurrent signals cannot create duplicate pending approvals.
        Product p=products.findForUpdate(productId).orElseThrow(()->new NoSuchElementException("Product not found"));
        var old=pricing.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(productId,trigger.name(),"PENDING");
        if(old.isPresent()) return view(old.get());
        RecommendationContext c=context(p,trigger); PricingStrategy.PricingResult result;
        try { result="LITELLM".equals(selector.current())?llm.suggestPrice(c):rules.suggestPrice(c); }
        catch(RuntimeException ex) { var f=rules.suggestPrice(c); result=new PricingStrategy.PricingResult(f.price(),f.direction(),f.confidence(),f.reasoning()+" AI output unavailable; safe rule fallback used.","AI_FALLBACK_RULE_BASED","LLM suggestion unavailable or invalid"); }
        var saved=pricing.save(new PricingSuggestion(productId,trigger,p.getCurrentPrice(),result.price(),result.direction(),result.confidence(),result.reasoning(),result.strategyName(),result.failureReason()));
        log.info("recommendation_created type=PRICING productId={} trigger={} strategy={} status=PENDING",productId,trigger,result.strategyName());
        return view(saved);
    }
    @Transactional public SuggestionView createReorder(String productId, Suggestion.Trigger trigger) {
        Product p=products.findForUpdate(productId).orElseThrow(()->new NoSuchElementException("Product not found"));
        var old=reorders.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(productId,trigger.name(),"PENDING");
        if(old.isPresent()) return view(old.get());
        RecommendationContext c=context(p,trigger); ReorderStrategy.ReorderResult result;
        try { result="LITELLM".equals(selector.current())?llm.suggestReorder(c):rules.suggestReorder(c); }
        catch(RuntimeException ex) { var f=rules.suggestReorder(c); result=new ReorderStrategy.ReorderResult(f.quantity(),f.leadTimeDays(),f.confidence(),f.reasoning()+" AI output unavailable; safe rule fallback used.","AI_FALLBACK_RULE_BASED","LLM suggestion unavailable or invalid"); }
        var saved=reorders.save(new ReorderSuggestion(productId,trigger,p.getStockLevel(),p.getReorderThreshold(),result.quantity(),result.leadTimeDays(),result.confidence(),result.reasoning(),result.strategyName(),result.failureReason()));
        log.info("recommendation_created type=REORDER productId={} trigger={} strategy={} status=PENDING",productId,trigger,result.strategyName());
        return view(saved);
    }
    @Transactional public List<SuggestionView> createBoth(String productId,Suggestion.Trigger trigger) { return List.of(createPricing(productId,trigger),createReorder(productId,trigger)); }
    @Transactional(readOnly=true) public List<SuggestionView> list(String productId) {
        if(!products.existsById(productId)) throw new NoSuchElementException("Product not found");
        List<SuggestionView> all=new ArrayList<>(); pricing.findByProductIdOrderByCreatedAtDesc(productId).stream().map(this::view).forEach(all::add); reorders.findByProductIdOrderByCreatedAtDesc(productId).stream().map(this::view).forEach(all::add);
        all.sort(Comparator.comparing(SuggestionView::createdAt,Comparator.nullsLast(Comparator.reverseOrder()))); return all;
    }
    @Transactional public SuggestionView reviewPricing(UUID id,boolean accept) {
        PricingSuggestion s=pricing.findForUpdate(id.toString()).orElseThrow(()->new NoSuchElementException("Pricing suggestion not found"));
        Product p=products.findForUpdate(s.getProductId()).orElseThrow(()->new NoSuchElementException("Product not found"));
        s.review(accept,reviewer()); if(accept) { p.setCurrentPrice(s.getRecommendedPrice()); products.save(p); }
        log.info("recommendation_reviewed type=PRICING productId={} suggestionId={} decision={}",s.getProductId(),id,accept?"ACCEPT":"REJECT");
        return view(pricing.save(s));
    }
    @Transactional public SuggestionView reviewReorder(UUID id,boolean accept) {
        ReorderSuggestion s=reorders.findForUpdate(id.toString()).orElseThrow(()->new NoSuchElementException("Reorder suggestion not found"));
        Product p=products.findForUpdate(s.getProductId()).orElseThrow(()->new NoSuchElementException("Product not found"));
        s.review(accept,reviewer()); if(accept) { p.setStockLevel(Math.addExact(p.getStockLevel(),s.getRecommendedQuantity())); products.save(p); }
        log.info("recommendation_reviewed type=REORDER productId={} suggestionId={} decision={}",s.getProductId(),id,accept?"ACCEPT":"REJECT");
        return view(reorders.save(s));
    }
    public boolean isDemandSpike(Product p) { return rules.isSpike(context(p,Suggestion.Trigger.DEMAND_SPIKE)); }
    private RecommendationContext context(Product p,Suggestion.Trigger trigger) { return new RecommendationContext(p,trigger,products.averagePeerDemand(p.getCategory(),p.getId())); }
    private String reviewer() {
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null||!authentication.isAuthenticated()||"anonymousUser".equals(authentication.getPrincipal())) throw new org.springframework.security.access.AccessDeniedException("A signed-in reviewer is required");
        return authentication.getName();
    }
    private SuggestionView view(PricingSuggestion s) { return new SuggestionView(s.getId(),s.getProductId(),"PRICING",s.getTriggerReason(),s.getStatus(),s.getRecommendedPrice(),null,s.getConfidence(),s.getReasoning(),s.getStrategyName(),s.getCreatedAt(),s.getReviewedBy()); }
    private SuggestionView view(ReorderSuggestion s) { return new SuggestionView(s.getId(),s.getProductId(),"REORDER",s.getTriggerReason(),s.getStatus(),null,s.getRecommendedQuantity(),s.getConfidence(),s.getReasoning()+" Estimated lead time: "+s.getSuggestedLeadTimeDays()+" days.",s.getStrategyName(),s.getCreatedAt(),s.getReviewedBy()); }
}
