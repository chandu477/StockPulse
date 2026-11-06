package com.stockpulse.integration.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockpulse.shared.logging.LogEvents;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Provider-specific HTTP for Groq, Ollama (OpenAI-compatible) and Gemini (brief Addendum B,
 * ADR-10). Returns raw model text; parsing, validation and fallback live in recommendation.ai.
 * Guards (ADR-4): connect/read timeouts, a per-minute call budget and a circuit breaker. Never logs
 * the prompt or the key. Must never be called inside a DB transaction (BR-2).
 */
@Component
public class LlmGateway {
  private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

  private final LlmProperties props;
  private final ObjectMapper mapper;
  private final RestClient http;
  private final CallBudget budget;
  private final CircuitBreaker breaker;

  public LlmGateway(LlmProperties props, ObjectMapper mapper, Clock clock) {
    this.props = props;
    this.mapper = mapper;
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(props.timeout().connectMs()))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofMillis(props.timeout().readMs()));
    this.http = RestClient.builder().requestFactory(factory).build();
    this.budget = new CallBudget(props.maxCallsPerMinute(), clock);
    this.breaker =
        CircuitBreaker.of(
            "llm",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(props.breaker().failureRateThreshold())
                .slidingWindowSize(props.breaker().slidingWindowSize())
                .minimumNumberOfCalls(Math.min(10, props.breaker().slidingWindowSize()))
                .waitDurationInOpenState(Duration.ofSeconds(props.breaker().openSeconds()))
                .recordException(
                    e -> e instanceof LlmException le && le.failure() != LlmFailure.EMPTY_RESPONSE)
                .build());
  }

  public boolean isConfigured() {
    String p = provider();
    return props.hasModel() && (p.equals("ollama") || props.hasKey());
  }

  public String provider() {
    return props.provider() == null ? "" : props.provider().toLowerCase(Locale.ROOT);
  }

  public String model() {
    return props.model();
  }

  /** One completion. Throws LlmException with a precise LlmFailure on any problem. */
  public String complete(String systemPrompt, String userPrompt) {
    if (!isConfigured()) {
      throw new LlmException(LlmFailure.NOT_CONFIGURED, "LLM provider/model/key not configured");
    }
    if (!budget.tryAcquire()) {
      throw new LlmException(LlmFailure.BUDGET_EXCEEDED, "LLM call budget exceeded");
    }
    long start = System.nanoTime();
    try {
      String text = breaker.executeSupplier(() -> call(systemPrompt, userPrompt));
      log.atInfo()
          .addKeyValue("event", LogEvents.LLM_CALL)
          .addKeyValue("provider", provider())
          .addKeyValue("model", props.model())
          .addKeyValue("latencyMs", (System.nanoTime() - start) / 1_000_000)
          .addKeyValue("outcome", "OK")
          .log("LLM call succeeded");
      return text;
    } catch (CallNotPermittedException e) {
      throw new LlmException(LlmFailure.CIRCUIT_OPEN, "LLM circuit breaker is open", e);
    } catch (LlmException e) {
      log.atWarn()
          .addKeyValue("event", LogEvents.LLM_CALL)
          .addKeyValue("provider", provider())
          .addKeyValue("model", props.model())
          .addKeyValue("latencyMs", (System.nanoTime() - start) / 1_000_000)
          .addKeyValue("outcome", e.failure().name())
          .log("LLM call failed: {}", e.failure());
      throw e;
    }
  }

  private String call(String system, String user) {
    try {
      return switch (provider()) {
        case "groq" -> openAiCompatible(system, user, base() + "/openai/v1/chat/completions");
        case "ollama", "openai" -> openAiCompatible(system, user, base() + "/v1/chat/completions");
        case "gemini" -> gemini(system, user);
        default ->
            throw new LlmException(LlmFailure.NOT_CONFIGURED, "Unknown provider " + provider());
      };
    } catch (RestClientResponseException e) {
      throw new LlmException(
          classify(e.getStatusCode()), "Provider HTTP " + e.getStatusCode().value(), e);
    } catch (ResourceAccessException e) {
      boolean timeout =
          e.getCause() instanceof HttpTimeoutException
              || e.getCause() instanceof SocketTimeoutException;
      throw new LlmException(
          timeout ? LlmFailure.TIMEOUT : LlmFailure.PROVIDER_ERROR, "Provider unreachable", e);
    }
  }

  private String openAiCompatible(String system, String user, String url) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", props.model());
    body.put("temperature", 0.2);
    body.putObject("response_format").put("type", "json_object");
    var messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content", system);
    messages.addObject().put("role", "user").put("content", user);
    RestClient.RequestBodySpec req = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
    if (props.hasKey()) {
      req = req.header("Authorization", "Bearer " + props.apiKey());
    }
    JsonNode response = req.body(body).retrieve().body(JsonNode.class);
    return nonEmpty(
        response == null ? null : response.at("/choices/0/message/content").asText(null));
  }

  private String gemini(String system, String user) {
    ObjectNode body = mapper.createObjectNode();
    body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
    body.putArray("contents")
        .addObject()
        .put("role", "user")
        .putArray("parts")
        .addObject()
        .put("text", user);
    ObjectNode config = body.putObject("generationConfig");
    config.put("temperature", 0.2);
    config.put("responseMimeType", "application/json");
    JsonNode response =
        http.post()
            .uri(base() + "/v1beta/models/" + props.model() + ":generateContent")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-goog-api-key", props.apiKey())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
    return nonEmpty(
        response == null ? null : response.at("/candidates/0/content/parts/0/text").asText(null));
  }

  private static String nonEmpty(String text) {
    if (text == null || text.isBlank()) {
      throw new LlmException(LlmFailure.EMPTY_RESPONSE, "Provider returned no text");
    }
    return text;
  }

  private static LlmFailure classify(HttpStatusCode status) {
    if (status.value() == 429) {
      return LlmFailure.RATE_LIMITED;
    }
    if (status.value() == 408 || status.value() == 504) {
      return LlmFailure.TIMEOUT;
    }
    return LlmFailure.PROVIDER_ERROR;
  }

  private String base() {
    String b = props.baseUrl() == null ? "" : props.baseUrl();
    return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
  }
}
