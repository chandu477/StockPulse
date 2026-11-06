package com.stockpulse.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.testsupport.FakeLlmServer;
import com.stockpulse.testsupport.FakeLlmServer.Mode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** AI strategy end to end against a fake OpenAI-compatible LLM: success and every fallback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class AiAdvisorIT {
  static final FakeLlmServer LLM = new FakeLlmServer();
  private static final AtomicInteger SEQ = new AtomicInteger();

  @DynamicPropertySource
  static void llm(DynamicPropertyRegistry r) {
    r.add("llm.provider", () -> "groq");
    r.add("llm.model", () -> "fake-model");
    r.add("llm.base-url", LLM::baseUrl);
    r.add("llm.api-key", () -> "test-key");
    r.add("llm.timeout.read-ms", () -> 1500);
    r.add("llm.breaker.failure-rate-threshold", () -> 100);
    r.add("llm.max-calls-per-minute", () -> 1000);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @AfterAll
  static void stop() {
    LLM.close();
  }

  @BeforeEach
  void useAi() throws Exception {
    LLM.mode(Mode.OK);
    strategies("{\"pricing\":\"ai\",\"reorder\":\"ai\"}");
  }

  @AfterEach
  void restore() throws Exception {
    strategies("{\"pricing\":\"rule\",\"reorder\":\"rule\"}");
  }

  private void strategies(String body) throws Exception {
    mvc.perform(
            put("/api/v1/admin/strategies").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
  }

  private String createProduct(int stock, int threshold, int velocity) throws Exception {
    String body =
        """
        {"sku":"SKU-AI-%d","name":"AI product","category":"HOME","currentPrice":40.00,
         "stockLevel":%d,"reorderThreshold":%d,"demandVelocity":%d}
        """
            .formatted(SEQ.incrementAndGet(), stock, threshold, velocity);
    return read(mvc.perform(
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("id")
        .asText();
  }

  private JsonNode suggest(String productId, String type) throws Exception {
    return read(
        mvc.perform(post("/api/v1/products/" + productId + "/suggest-" + type))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode read(String s) throws Exception {
    return json.readTree(s);
  }

  @Test
  void aiAdviceIsUsedWhenValid() throws Exception {
    String id = createProduct(50, 10, 2);
    JsonNode pricing = suggest(id, "pricing");
    assertThat(pricing.get("source").asText()).isEqualTo("AI");
    assertThat(pricing.get("strategyKey").asText()).isEqualTo("ai");
    assertThat(pricing.get("recommendedPrice").decimalValue()).isEqualByComparingTo("42.00");
    assertThat(pricing.get("reasoning").asText()).startsWith("AI:");

    JsonNode reorder = suggest(id, "reorder");
    assertThat(reorder.get("source").asText()).isEqualTo("AI");
    assertThat(reorder.get("recommendedQuantity").asInt()).isEqualTo(40);
    assertThat(reorder.get("leadTimeDays").asInt()).isEqualTo(5);
  }

  @Test
  void codeFencedJsonIsStillAccepted() throws Exception {
    LLM.mode(Mode.FENCED);
    assertThat(suggest(createProduct(50, 10, 2), "pricing").get("source").asText()).isEqualTo("AI");
  }

  @Test
  void everyFailureFallsBackToRulesWithItsReason() throws Exception {
    assertFallback(Mode.GARBAGE, "pricing", "INVALID_JSON");
    assertFallback(Mode.RATE_LIMITED, "pricing", "RATE_LIMITED");
    assertFallback(Mode.SERVER_ERROR, "reorder", "PROVIDER_ERROR");
    assertFallback(Mode.ABSURD_PRICE, "pricing", "OUT_OF_BOUNDS");
    assertFallback(Mode.HOLD_WITH_CHANGE, "pricing", "OUT_OF_BOUNDS");
    assertFallback(Mode.FRACTIONAL_QUANTITY, "reorder", "OUT_OF_BOUNDS");
    assertFallback(Mode.EMPTY, "pricing", "EMPTY_RESPONSE");
    assertFallback(Mode.SLOW, "pricing", "TIMEOUT");
  }

  private void assertFallback(Mode mode, String type, String reason) throws Exception {
    LLM.mode(mode);
    JsonNode s = suggest(createProduct(50, 10, 2), type);
    assertThat(s.get("source").asText()).as(mode.name()).isEqualTo("RULE_FALLBACK");
    assertThat(s.get("fallbackReason").asText()).as(mode.name()).isEqualTo(reason);
    assertThat(s.get("strategyKey").asText()).isEqualTo("ai->rule");
    assertThat(s.get("reasoning").asText()).startsWith("Rule:");
  }

  @Test
  void agenticLoopUsesTheTriggerSpecificPrompt() throws Exception {
    String id = createProduct(11, 10, 0);
    int before = LLM.requests().size();
    mvc.perform(
            post("/api/v1/products/" + id + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
        .andExpect(status().isCreated());

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() ->
            read(mvc.perform(get("/api/v1/pricing-suggestions?status=PENDING&productId=" + id))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                    .size()
                == 1);
    JsonNode pricing =
        read(mvc.perform(get("/api/v1/pricing-suggestions?productId=" + id))
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get(0);
    assertThat(pricing.get("triggerReason").asText()).isEqualTo("INVENTORY_LOW");
    assertThat(pricing.get("source").asText()).isEqualTo("AI");
    var prompts = LLM.requests().subList(before, LLM.requests().size());
    assertThat(prompts).anyMatch(r -> r.body().contains("SITUATION: INVENTORY LOW"));
    assertThat(prompts).noneMatch(r -> r.body().contains("SITUATION: DEMAND SPIKE"));
    assertThat(prompts).allMatch(r -> "Bearer test-key".equals(r.authorization()));
  }
}
