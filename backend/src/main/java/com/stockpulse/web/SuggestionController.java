package com.stockpulse.web;

import com.stockpulse.api.ApiModels.*;
import com.stockpulse.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class SuggestionController {
    public record ReviewRequest(@NotBlank String decision) { }
    private final RecommendationService recommendations;
    public SuggestionController(RecommendationService recommendations) { this.recommendations=recommendations; }
    @PatchMapping("/api/v1/pricing-suggestions/{id}") public Envelope<SuggestionView> pricing(@PathVariable UUID id,@Valid @RequestBody ReviewRequest r) { return new Envelope<>(recommendations.reviewPricing(id,accept(r))); }
    @PatchMapping("/api/v1/reorder-suggestions/{id}") public Envelope<SuggestionView> reorder(@PathVariable UUID id,@Valid @RequestBody ReviewRequest r) { return new Envelope<>(recommendations.reviewReorder(id,accept(r))); }
    private boolean accept(ReviewRequest r) { if("ACCEPT".equalsIgnoreCase(r.decision())) return true; if("REJECT".equalsIgnoreCase(r.decision())) return false; throw new IllegalArgumentException("decision must be ACCEPT or REJECT"); }
}
