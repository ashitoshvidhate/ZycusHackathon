package com.stockpulse;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.stockpulse.service.LiteLlmGateway;
import com.stockpulse.service.StrategySelector;
import com.stockpulse.repo.RecommendationJobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest @AutoConfigureMockMvc @WithMockUser(authorities="ROLE_ADMIN")
class RecommendationFlowTest {
    @Autowired MockMvc mvc;
    @MockBean LiteLlmGateway gateway;
    @Autowired StrategySelector selector;
    @Autowired RecommendationJobRepository jobs;
    @Autowired CircuitBreakerRegistry circuitBreakers;
    @Autowired RetryRegistry retries;
    @Autowired RateLimiterRegistry rateLimiters;

    @Test void llmResilienceLimitsAreConfiguredAndBounded() {
        assertEquals(50.0f,circuitBreakers.circuitBreaker("llmGateway").getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(3,retries.retry("llmGateway").getRetryConfig().getMaxAttempts());
        assertEquals(10,rateLimiters.rateLimiter("llmGateway").getRateLimiterConfig().getLimitForPeriod());
    }

    @Test void apiRequiresJwtAuthenticationAndEnforcesOperationScopes() throws Exception {
        mvc.perform(get("/api/v1/products/PRD-003").with(anonymous()))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"))
                .andExpect(jsonPath("$.code",is("AUTHENTICATION_REQUIRED")));
        mvc.perform(get("/api/v1/products/PRD-003").header("Authorization","Bearer malformed-token").with(anonymous()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code",is("INVALID_TOKEN")));
        mvc.perform(get("/api/v1/products/PRD-003").with(user("reader").authorities(new SimpleGrantedAuthority("SCOPE_stockpulse.read"))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/products/PRD-003/stock").with(user("reader").authorities(new SimpleGrantedAuthority("SCOPE_stockpulse.read")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":10}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code",is("ACCESS_DENIED")));
    }

    @Test void pricingAndReorderAreIndependentAndAcceptanceHasOnlyItsOwnEffect() throws Exception {
        String pricingId=id(mvc.perform(post("/api/v1/products/PRD-003/suggest-pricing")).andExpect(status().isOk()).andExpect(jsonPath("$.data.type",is("PRICING"))).andReturn().getResponse().getContentAsString());
        String reorderId=id(mvc.perform(post("/api/v1/products/PRD-003/suggest-reorder")).andExpect(status().isOk()).andExpect(jsonPath("$.data.type",is("REORDER"))).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/products/PRD-003/suggestions")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()",is(2)));
        mvc.perform(patch("/api/v1/pricing-suggestions/{id}",pricingId).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status",is("ACCEPTED"))).andExpect(jsonPath("$.data.reviewedBy",is("user")));
        // Accepting price approval must never apply the independent reorder suggestion.
        mvc.perform(get("/api/v1/products/PRD-003")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice",is(27.49))).andExpect(jsonPath("$.data.stockLevel",is(8)));
        mvc.perform(patch("/api/v1/reorder-suggestions/{id}",reorderId).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status",is("ACCEPTED")));
        mvc.perform(get("/api/v1/products/PRD-003")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice",is(27.49))).andExpect(jsonPath("$.data.stockLevel",is(45)));
        mvc.perform(patch("/api/v1/pricing-suggestions/{id}",pricingId).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code",is("OPERATION_CONFLICT")));
    }

    @Test void orderTriggerProducesSeparateSuggestionsAndValidationUsesStableErrorCodes() throws Exception {
        mvc.perform(patch("/api/v1/products/PRD-008/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":30}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/products/PRD-008/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/products/PRD-008/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}" )).andExpect(status().isOk());
        // Generation happens after commit on a bounded async executor; poll only for this eventual-consistency contract.
        boolean completed=false;
        for(int i=0;i<30;i++) {
            var result=mvc.perform(get("/api/v1/products/PRD-008/suggestions")).andReturn();
            var body=result.getResponse().getContentAsString();
            if(body.contains("PRICING")&&body.contains("REORDER")&&jobs.countByProductIdAndStatus("PRD-008","COMPLETED")>=1) { completed=true; break; }
            Thread.sleep(100);
        }
        org.junit.jupiter.api.Assertions.assertTrue(completed,"Async signal should persist both independently reviewable suggestions");
        mvc.perform(get("/api/v1/products/PRD-008/suggestions")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()",is(2)));
        mvc.perform(post("/api/v1/products/PRD-004/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code",is("VALIDATION_FAILED")));
        mvc.perform(post("/api/v1/products/PRD-006/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/products/unknown-product")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code",is("PRODUCT_NOT_FOUND")));
    }
    @Test void strategyCanSwitchAtRuntimeAndInvalidAiOutputFallsBackToRules() throws Exception {
        when(gateway.complete(anyString())).thenReturn(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode()
                .put("recommendedPrice",0).put("direction","HOLD").put("confidence",0.9).put("reasoning","Invalid zero price"));
        try {
            mvc.perform(put("/api/v1/strategy").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"LITELLM\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data",is("LITELLM")));
            mvc.perform(post("/api/v1/products/PRD-005/suggest-pricing"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.strategyName",is("AI_FALLBACK_RULE_BASED")));
        } finally { selector.select("RULE_BASED"); }
    }

    @Test void productCreateValidationAndCatalogFiltersWork() throws Exception {
        String sku="TEST-"+java.util.UUID.randomUUID();
        String body="{\"sku\":\""+sku+"\",\"name\":\"Catalog test\",\"category\":\"APPAREL\",\"currentPrice\":19.99,\"costPrice\":8.00,\"supplierId\":\"SUP-1\",\"stockLevel\":12,\"reorderThreshold\":5,\"demandVelocity\":2}";
        String productId=id(mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.category",is("APPAREL")))
                .andExpect(jsonPath("$.data.costPrice",is(8.0))).andExpect(jsonPath("$.data.supplierId",is("SUP-1")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/products").param("category","apparel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='"+productId+"')].sku").value(org.hamcrest.Matchers.hasItem(sku)));
        mvc.perform(get("/api/v1/products").param("status","ACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='"+productId+"')].status").value(org.hamcrest.Matchers.hasItem("ACTIVE")));
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code",is("OPERATION_CONFLICT")));
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("19.99","0"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code",is("VALIDATION_FAILED")));
    }

    @Test void stockPatchCreatesLowStockSuggestionsAndOutOfStockState() throws Exception {
        String sku="LOW-"+java.util.UUID.randomUUID();
        String body="{\"sku\":\""+sku+"\",\"name\":\"Low stock test\",\"category\":\"HOME\",\"currentPrice\":20.00,\"stockLevel\":8,\"reorderThreshold\":5,\"demandVelocity\":0}";
        String productId=id(mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(patch("/api/v1/products/{id}/stock",productId).contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status",is("OUT_OF_STOCK")));
        awaitSuggestions(productId,"INVENTORY_LOW");
        mvc.perform(get("/api/v1/products/{id}/suggestions",productId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()",is(2)))
                .andExpect(jsonPath("$.data[?(@.type=='PRICING')].triggerReason").value(org.hamcrest.Matchers.hasItem("INVENTORY_LOW")))
                .andExpect(jsonPath("$.data[?(@.type=='REORDER')].triggerReason").value(org.hamcrest.Matchers.hasItem("INVENTORY_LOW")));
        mvc.perform(patch("/api/v1/products/{id}/stock",productId).contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code",is("VALIDATION_FAILED")));
    }

    @Test void rejectLeavesProductUnchangedAndReviewRecordsActor() throws Exception {
        String productId=createTestProduct("REJECT", "9.99");
        String pricingId=id(mvc.perform(post("/api/v1/products/{id}/suggest-pricing",productId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(patch("/api/v1/pricing-suggestions/{id}",pricingId).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status",is("REJECTED"))).andExpect(jsonPath("$.data.reviewedBy",is("user")));
        mvc.perform(get("/api/v1/products/{id}",productId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice",is(9.99)));
        mvc.perform(patch("/api/v1/pricing-suggestions/{id}",pricingId).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code",is("OPERATION_CONFLICT")));
    }

    @Test void validAiPriceAndReorderResultsAreValidatedAndPersisted() throws Exception {
        var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        String productId=createTestProduct("AI", "20.00");
        when(gateway.complete(anyString())).thenReturn(mapper.createObjectNode().put("recommendedPrice",29.99).put("direction","INCREASE").put("confidence",0.9).put("reasoning","Measured increase"))
                .thenReturn(mapper.createObjectNode().put("recommendedQuantity",17).put("leadTimeDays",12).put("confidence",0.85).put("reasoning","Replenish stock"));
        try {
            selector.select("LITELLM");
            mvc.perform(post("/api/v1/products/{id}/suggest-pricing",productId)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.strategyName",is("LITELLM"))).andExpect(jsonPath("$.data.recommendedPrice",is(29.99)))
                    .andExpect(jsonPath("$.data.confidence",is(0.9)));
            mvc.perform(post("/api/v1/products/{id}/suggest-reorder",productId)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.strategyName",is("LITELLM"))).andExpect(jsonPath("$.data.recommendedQuantity",is(17)))
                    .andExpect(jsonPath("$.data.reasoning",org.hamcrest.Matchers.containsString("12 days")));
            verify(gateway,times(2)).complete(anyString());
        } finally { selector.select("RULE_BASED"); }
    }

    @Test void endToEndOrderSignalRequiresHumanApprovalToChangePrice() throws Exception {
        String productId=createTestProduct("DEMO", "20.00");
        mvc.perform(patch("/api/v1/products/{id}/stock",productId).contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":6}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/products/{id}/orders",productId).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stockLevel",is(4))).andExpect(jsonPath("$.data.status",is("LOW_STOCK")));
        awaitSuggestions(productId,"INVENTORY_LOW");
        var response=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(mvc.perform(get("/api/v1/products/{id}/suggestions",productId)).andReturn().getResponse().getContentAsString());
        String pricingId=response.path("data").findValuesAsText("id").get(0);
        for(int i=0;i<response.path("data").size();i++) if("PRICING".equals(response.path("data").get(i).path("type").asText())) pricingId=response.path("data").get(i).path("id").asText();
        var pending=pricingId;
        mvc.perform(get("/api/v1/products/{id}",productId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice",is(20.0)));
        mvc.perform(patch("/api/v1/pricing-suggestions/{id}",pending).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status",is("ACCEPTED")));
        mvc.perform(get("/api/v1/products/{id}",productId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice",is(22.0)));
    }

    @Test void strategyMutationRequiresAdminAndBadNameHasStableValidationError() throws Exception {
        mvc.perform(put("/api/v1/strategy").with(user("reader").authorities(new SimpleGrantedAuthority("SCOPE_stockpulse.read")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"LITELLM\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code",is("ACCESS_DENIED")));
        mvc.perform(put("/api/v1/strategy").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code",is("INVALID_REQUEST")));
    }

    private void awaitSuggestions(String productId,String trigger) throws Exception {
        boolean completed=false;
        for(int i=0;i<40;i++) {
            var result=mvc.perform(get("/api/v1/products/{id}/suggestions",productId)).andReturn();
            var body=result.getResponse().getContentAsString();
            if(body.contains("PRICING")&&body.contains("REORDER")&&body.contains(trigger)&&jobs.countByProductIdAndStatus(productId,"COMPLETED")>=1) { completed=true; break; }
            Thread.sleep(100);
        }
        org.junit.jupiter.api.Assertions.assertTrue(completed,"Expected durable asynchronous "+trigger+" job to complete");
    }
    private String createTestProduct(String prefix,String price) throws Exception {
        String sku=prefix+"-"+java.util.UUID.randomUUID();
        String body="{\"sku\":\""+sku+"\",\"name\":\""+prefix+" test\",\"category\":\"HOME\",\"currentPrice\":"+price+",\"stockLevel\":10,\"reorderThreshold\":5,\"demandVelocity\":0}";
        return id(mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String id(String body) throws Exception { return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).path("data").path("id").asText(); }
}
