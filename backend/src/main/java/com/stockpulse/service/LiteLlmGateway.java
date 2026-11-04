package com.stockpulse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Private-server gateway with bounded timeout, request rate, retries, and failure circuit. */
@Component
public class LiteLlmGateway {
    private static final Logger log=LoggerFactory.getLogger(LiteLlmGateway.class);
    private final String baseUrl,key,model,productHeader,cookie;
    private final int connectTimeout,readTimeout;
    private final ObjectMapper mapper;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final RateLimiterRegistry rateLimiters;
    public LiteLlmGateway(@Value("${llm.base-url:}") String baseUrl,@Value("${llm.api-key:}") String key,
            @Value("${llm.model:}") String model,@Value("${llm.product-header:}") String productHeader,
            @Value("${llm.cookie:}") String cookie,@Value("${llm.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${llm.read-timeout-ms:15000}") int readTimeout,ObjectMapper mapper,CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,RateLimiterRegistry rateLimiters) {
        this.baseUrl=baseUrl; this.key=key; this.model=model; this.productHeader=productHeader; this.cookie=cookie;
        this.connectTimeout=connectTimeout; this.readTimeout=readTimeout; this.mapper=mapper; this.circuitBreakers=circuitBreakers;
        this.retries=retries; this.rateLimiters=rateLimiters;
    }

    @PostConstruct void registerResilienceLogs() {
        circuitBreakers.circuitBreaker("llmGateway").getEventPublisher().onStateTransition(event->
                log.warn("llm_circuit_state_changed stateTransition={}",event.getStateTransition()));
        retries.retry("llmGateway").getEventPublisher().onRetry(event->
                log.warn("llm_retry attempt={} errorType={}",event.getNumberOfRetryAttempts(),event.getLastThrowable()==null?"unknown":event.getLastThrowable().getClass().getSimpleName()));
        rateLimiters.rateLimiter("llmGateway").getEventPublisher().onFailure(event->
                log.warn("llm_rate_limit_rejected eventType={}",event.getEventType()));
    }

    /** Resilience4j exceptions deliberately propagate to the service, which creates a rule-based suggestion. */
    @RateLimiter(name="llmGateway")
    @Retry(name="llmGateway")
    @CircuitBreaker(name="llmGateway")
    public JsonNode complete(String prompt) {
        if(baseUrl.isBlank()||key.isBlank()||model.isBlank()) throw new IllegalStateException("LLM gateway is not configured");
        try { return requestOnce(prompt); }
        catch(IOException e) { throw new IllegalStateException("LLM response was not valid JSON",e); }
    }

    private JsonNode requestOnce(String prompt) throws IOException {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout)); factory.setReadTimeout(Duration.ofMillis(readTimeout));
        RestClient client=RestClient.builder().baseUrl(baseUrl.replaceAll("/$","")).requestFactory(factory).build();
        var spec=client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).header("Authorization","Bearer "+key);
        if(!productHeader.isBlank()) spec.header("product",productHeader);
        if(!cookie.isBlank()) spec.header("Cookie",cookie);
        String response;
        try {
            response=spec.body(Map.of("model",model,"messages",new Object[]{Map.of("role","user","content",prompt)},"temperature",0))
                    .retrieve().body(String.class);
        } catch(RestClientException e) {
            // The exception type/status is useful for metrics and retry policy; never log the request or headers.
            throw e;
        }
        JsonNode root=mapper.readTree(response); JsonNode content=root.path("choices").path(0).path("message").path("content");
        if(!content.isTextual()) throw new IllegalStateException("LLM returned no text content");
        String text=content.asText().trim().replaceAll("^```(?:json)?\\s*","").replaceAll("\\s*```$","");
        return mapper.readTree(text);
    }
}
