package com.stockpulse.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.PricingStrategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end through the HTTP API on H2 with in-process messaging: order -> outbox -> loop ->
 * suggestions -> accept. Each test creates its own products so tests stay independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class AgenticLoopIT {
  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  /** A strategy that always fails, to prove the fallback path never drops a suggestion. */
  @TestConfiguration
  static class FailingStrategyConfig {
    @Bean
    PricingStrategy explodingPricingStrategy() {
      return new PricingStrategy() {
        @Override
        public String key() {
          return "exploding";
        }

        @Override
        public PricingAdvice advise(CommerceContext context) {
          throw new IllegalStateException("simulated provider failure");
        }
      };
    }
  }

  // ---------------------------------------------------------------- helpers

  private String createProduct(String category, int stock, int threshold, int velocity)
      throws Exception {
    String body =
        """
        {"sku":"SKU-IT-%d","name":"IT product","category":"%s","currentPrice":20.00,
         "stockLevel":%d,"reorderThreshold":%d,"demandVelocity":%d}
        """
            .formatted(SEQ.incrementAndGet(), category, stock, threshold, velocity);
    JsonNode created =
        read(
            mvc.perform(
                    post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    return created.get("id").asText();
  }

  private ResultActions order(String id, int qty) throws Exception {
    return mvc.perform(
        post("/api/v1/products/" + id + "/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"quantity\":" + qty + "}"));
  }

  private JsonNode list(String type, String productId) throws Exception {
    return read(
        mvc.perform(get("/api/v1/" + type + "?status=PENDING&productId=" + productId))
            .andExpect(status().isOk()));
  }

  private JsonNode awaitPending(String type, String productId, int count) {
    await().atMost(Duration.ofSeconds(10)).until(() -> list(type, productId).size() >= count);
    try {
      return list(type, productId);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode product(String id) throws Exception {
    return read(mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk()));
  }

  private ResultActions decide(String type, long id, String decision) throws Exception {
    return mvc.perform(
        patch("/api/v1/" + type + "/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + decision + "\"}"));
  }

  private JsonNode read(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------- tests

  @Test
  void orderBelowThresholdCreatesBothSuggestionsAsyncAndOnlyOnce() throws Exception {
    String id = createProduct("APPAREL", 16, 15, 0);

    order(id, 2).andExpect(status().isCreated()).andExpect(jsonPath("$.stockLevel").value(14));

    JsonNode pricing = awaitPending("pricing-suggestions", id, 1);
    JsonNode reorder = awaitPending("reorder-suggestions", id, 1);
    assertThat(pricing.get(0).get("triggerReason").asText()).isEqualTo("INVENTORY_LOW");
    assertThat(pricing.get(0).get("recommendedPrice").decimalValue()).isEqualByComparingTo("22.00");
    assertThat(reorder.get(0).get("recommendedQuantity").asInt()).isEqualTo(31);
    assertThat(product(id).get("status").asText()).isEqualTo("PRICE_REVIEW_PENDING");

    // Level-triggered + idempotent: another low-stock order adds no duplicate.
    order(id, 1).andExpect(status().isCreated());
    Thread.sleep(1500);
    assertThat(list("pricing-suggestions", id)).hasSize(1);
    assertThat(list("reorder-suggestions", id)).hasSize(1);
  }

  @Test
  void acceptingPriceUpdatesProductAndSecondDecisionConflicts() throws Exception {
    String id = createProduct("HOME", 10, 15, 0);
    order(id, 1).andExpect(status().isCreated());
    long pricingId = awaitPending("pricing-suggestions", id, 1).get(0).get("id").asLong();
    long reorderId = awaitPending("reorder-suggestions", id, 1).get(0).get("id").asLong();

    decide("pricing-suggestions", pricingId, "ACCEPTED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
    JsonNode p = product(id);
    assertThat(p.get("currentPrice").decimalValue()).isEqualByComparingTo("22.00");
    assertThat(p.get("status").asText()).isEqualTo("PRICE_REVIEW_PENDING"); // reorder still pending

    decide("pricing-suggestions", pricingId, "REJECTED")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_PENDING"));

    decide("reorder-suggestions", reorderId, "ACCEPTED").andExpect(status().isOk());
    p = product(id);
    assertThat(p.get("stockLevel").asInt()).isEqualTo(9 + 36);
    assertThat(p.get("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void rejectingLeavesPriceUnchanged() throws Exception {
    String id = createProduct("HOME", 10, 15, 0);
    order(id, 1);
    long pricingId = awaitPending("pricing-suggestions", id, 1).get(0).get("id").asLong();

    decide("pricing-suggestions", pricingId, "REJECTED").andExpect(status().isOk());

    assertThat(product(id).get("currentPrice").decimalValue()).isEqualByComparingTo("20.00");
  }

  @Test
  void demandSpikeFiresAgainstPeerAverage() throws Exception {
    // Self-contained: give the category a peer with real demand (independent of seed data).
    createProduct("ELECTRONICS", 500, 10, 10);
    String id = createProduct("ELECTRONICS", 500, 10, 5000);

    order(id, 1).andExpect(status().isCreated());

    JsonNode pricing = awaitPending("pricing-suggestions", id, 1);
    assertThat(pricing.get(0).get("triggerReason").asText()).isEqualTo("DEMAND_SPIKE");
    assertThat(awaitPending("reorder-suggestions", id, 1).get(0).get("triggerReason").asText())
        .isEqualTo("DEMAND_SPIKE");
  }

  @Test
  void concurrentOrdersProduceExactlyOnePendingPair() throws Exception {
    String id = createProduct("APPAREL", 100, 95, 0);
    int threads = 12;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      results.add(
          pool.submit(() -> {
            start.await();
            return order(id, 1).andReturn().getResponse().getStatus();
          }));
    }
    start.countDown();
    for (Future<Integer> f : results) {
      assertThat(f.get()).isEqualTo(201);
    }
    pool.shutdown();

    awaitPending("pricing-suggestions", id, 1);
    Thread.sleep(2000);
    assertThat(list("pricing-suggestions", id)).hasSize(1);
    assertThat(list("reorder-suggestions", id)).hasSize(1);
    assertThat(product(id).get("stockLevel").asInt()).isEqualTo(100 - threads);
  }

  @Test
  void recalculateReplacesThePendingSuggestion() throws Exception {
    String id = createProduct("HOME", 50, 10, 0);
    long first =
        read(mvc.perform(post("/api/v1/products/" + id + "/suggest-pricing"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triggerReason").value("MANUAL"))
                .andExpect(jsonPath("$.direction").value("HOLD")))
            .get("id")
            .asLong();
    long second =
        read(mvc.perform(post("/api/v1/products/" + id + "/suggest-pricing"))
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();

    assertThat(second).isNotEqualTo(first);
    JsonNode pending = list("pricing-suggestions", id);
    assertThat(pending).hasSize(1);
    assertThat(pending.get(0).get("id").asLong()).isEqualTo(second);
    JsonNode all = read(mvc.perform(get("/api/v1/pricing-suggestions?productId=" + id)));
    assertThat(all.findValuesAsText("status")).containsExactlyInAnyOrder("PENDING", "SUPERSEDED");
  }

  @Test
  void aDifferentTriggerRecalculatesIntoOneRecommendation() throws Exception {
    String id = createProduct("APPAREL", 20, 10, 0);
    mvc.perform(post("/api/v1/products/" + id + "/suggest-pricing"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/products/" + id + "/suggest-reorder"))
        .andExpect(status().isCreated());

    mvc.perform(
            patch("/api/v1/products/" + id + "/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockLevel\":5}"))
        .andExpect(status().isOk());

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() ->
            list("pricing-suggestions", id).size() == 1
                && "INVENTORY_LOW"
                    .equals(list("pricing-suggestions", id).get(0).get("triggerReason").asText())
                && "INVENTORY_LOW"
                    .equals(list("reorder-suggestions", id).get(0).get("triggerReason").asText()));
    assertThat(list("reorder-suggestions", id)).hasSize(1);
    assertThat(list("reorder-suggestions", id).get(0).get("recommendedQuantity").asInt())
        .isEqualTo(25);
  }

  @Test
  void failingStrategyFallsBackToRulesAndIsSwitchableAtRuntime() throws Exception {
    String id = createProduct("HOME", 50, 10, 0);
    mvc.perform(
            put("/api/v1/admin/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricing\":\"exploding\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricing").value("exploding"));
    try {
      mvc.perform(post("/api/v1/products/" + id + "/suggest-pricing"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.source").value(SuggestionSource.RULE_FALLBACK.name()))
          .andExpect(jsonPath("$.fallbackReason").value(FallbackReason.PROVIDER_ERROR.name()))
          .andExpect(jsonPath("$.strategyKey").value("exploding->rule"));
    } finally {
      mvc.perform(
              put("/api/v1/admin/strategies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"pricing\":\"rule\"}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void errorsUseTheStandardShape() throws Exception {
    String id = createProduct("HOME", 1, 0, 0);
    order(id, 5)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.path").value("/api/v1/products/" + id + "/orders"))
        .andExpect(jsonPath("$.errors").isArray());

    order(id, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("quantity"));

    mvc.perform(
            post("/api/v1/products/" + id + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"price\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mvc.perform(get("/api/v1/products/NOPE"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

    mvc.perform(get("/api/v1/products?status=WRONG"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mvc.perform(
            put("/api/v1/admin/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricing\":\"nope\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_STRATEGY"));

    mvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sku\":\"SKU-TOP-002\",\"name\":\"dup\",\"category\":\"APPAREL\","
                        + "\"currentPrice\":1,\"stockLevel\":1,\"reorderThreshold\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
  }

  @Test
  void seedDataIsLoaded() throws Exception {
    mvc.perform(get("/api/v1/products?category=APPAREL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'PRD-003')].stockLevel").exists());
  }
}
