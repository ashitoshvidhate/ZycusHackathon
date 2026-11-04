package com.stockpulse.web;

import com.stockpulse.api.ApiModels.Envelope;
import com.stockpulse.api.ApiModels.StrategyRequest;
import com.stockpulse.service.StrategySelector;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {
    private final StrategySelector selector;
    public StrategyController(StrategySelector selector) { this.selector=selector; }
    @GetMapping public Envelope<String> current() { return new Envelope<>(selector.current()); }
    @PutMapping public Envelope<String> select(@Valid @RequestBody StrategyRequest request) { return new Envelope<>(selector.select(request.name())); }
}
