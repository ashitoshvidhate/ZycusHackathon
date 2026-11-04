package com.stockpulse.service;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StrategySelector {
    private final AtomicReference<String> active;
    public StrategySelector(@Value("${stockpulse.recommendation-strategy:RULE_BASED}") String initial) { active=new AtomicReference<>(normalize(initial)); }
    public String current() { return active.get(); }
    public String select(String strategy) { active.set(normalize(strategy)); return active.get(); }
    private String normalize(String value) { String normalized=value==null?"":value.trim().toUpperCase(Locale.ROOT); if(!normalized.equals("RULE_BASED")&&!normalized.equals("LITELLM")) throw new IllegalArgumentException("Strategy must be RULE_BASED or LITELLM"); return normalized; }
}
