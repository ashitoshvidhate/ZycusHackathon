package com.stockpulse.web;

import com.stockpulse.api.ApiModels.*;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.Suggestion;
import com.stockpulse.repo.ProductRepository;
import com.stockpulse.service.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductRepository products; private final RecommendationService recommendations; private final RecommendationJobService jobs;
    public ProductController(ProductRepository products,RecommendationService recommendations,RecommendationJobService jobs) { this.products=products; this.recommendations=recommendations; this.jobs=jobs; }
    @GetMapping public Envelope<List<ProductView>> all(@RequestParam(required=false) String category,@RequestParam(required=false) String status) {
        return new Envelope<>(products.findAll().stream().filter(p->category==null||p.getCategory().equalsIgnoreCase(category)).filter(p->status==null||p.getStatus().equalsIgnoreCase(status)).map(this::view).toList());
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Envelope<ProductView> create(@Valid @RequestBody ProductCreateRequest r) {
        String sku=r.sku().trim(); if(products.existsBySkuIgnoreCase(sku)) throw new IllegalStateException("SKU already exists");
        Product p=new Product(UUID.randomUUID().toString(),sku,r.name().trim(),r.category().trim().toUpperCase(),r.currentPrice(),r.costPrice(),r.supplierId(),r.stockLevel(),r.reorderThreshold(),r.demandVelocity(),"ACTIVE");
        if(p.getStockLevel()<p.getReorderThreshold()) p.setStockLevel(p.getStockLevel());
        return new Envelope<>(view(products.save(p)));
    }
    @GetMapping("/{id}") public Envelope<ProductView> one(@PathVariable String id) { return new Envelope<>(view(products.findById(id).orElseThrow(()->new NoSuchElementException("Product not found")))); }
    @PatchMapping("/{id}/stock") @Transactional
    public Envelope<ProductView> stock(@PathVariable String id,@Valid @RequestBody StockRequest request) {
        Product p=products.findForUpdate(id).orElseThrow(()->new NoSuchElementException("Product not found")); p.setStockLevel(request.stockLevel());
        Product saved=products.save(p); if(saved.getStockLevel()<saved.getReorderThreshold()) jobs.enqueue(id,Suggestion.Trigger.INVENTORY_LOW);
        return new Envelope<>(view(saved));
    }
    @PostMapping("/{id}/orders") @Transactional
    public Envelope<ProductView> order(@PathVariable String id,@Valid @RequestBody OrderRequest request) {
        Product p=products.findForUpdate(id).orElseThrow(()->new NoSuchElementException("Product not found"));
        if(request.quantity()>p.getStockLevel()) throw new IllegalStateException("Insufficient stock for this order");
        p.setStockLevel(p.getStockLevel()-request.quantity()); p.setDemandVelocity(p.getDemandVelocity().add(BigDecimal.valueOf(request.quantity())));
        Product saved=products.save(p); Suggestion.Trigger trigger=null;
        if(saved.getStockLevel()<saved.getReorderThreshold()) trigger=Suggestion.Trigger.INVENTORY_LOW;
        else if(recommendations.isDemandSpike(saved)) trigger=Suggestion.Trigger.DEMAND_SPIKE;
        if(trigger!=null) jobs.enqueue(id,trigger);
        return new Envelope<>(view(saved));
    }
    @GetMapping("/{id}/suggestions") public Envelope<List<SuggestionView>> suggestions(@PathVariable String id) { return new Envelope<>(recommendations.list(id)); }
    @PostMapping("/{id}/suggest-pricing") public Envelope<SuggestionView> pricing(@PathVariable String id) { return new Envelope<>(recommendations.createPricing(id,Suggestion.Trigger.MANUAL)); }
    @PostMapping("/{id}/suggest-reorder") public Envelope<SuggestionView> reorder(@PathVariable String id) { return new Envelope<>(recommendations.createReorder(id,Suggestion.Trigger.MANUAL)); }
    private ProductView view(Product p) { return new ProductView(p.getId(),p.getSku(),p.getName(),p.getCategory(),p.getCurrentPrice(),p.getCostPrice(),p.getSupplierId(),p.getStockLevel(),p.getReorderThreshold(),p.getDemandVelocity(),p.getStatus()); }
}
