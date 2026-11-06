package com.stockpulse.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.testsupport.FakeLlmServer;
import com.stockpulse.testsupport.FakeLlmServer.Mode;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {
  private final FakeLlmServer server = new FakeLlmServer();

  @AfterEach
  void stop() {
    server.close();
  }

  private LlmGateway gateway(String key, int budget, int window) {
    return new LlmGateway(
        new LlmProperties(
            "groq",
            "test-model",
            server.baseUrl(),
            key,
            new LlmProperties.Timeout(500, 1000),
            budget,
            new LlmProperties.Breaker(50, window, 30)),
        new ObjectMapper(),
        Clock.systemUTC());
  }

  private LlmFailure failureOf(LlmGateway g) {
    try {
      g.complete("system", "user");
    } catch (LlmException e) {
      return e.failure();
    }
    throw new AssertionError("expected failure");
  }

  @Test
  void sendsOpenAiCompatibleRequestWithBearerKeyAndJsonMode() {
    String text = gateway("secret-key", 30, 20).complete("SYS", "current price: 10.00");
    assertThat(text).contains("recommendedPrice");
    var req = server.requests().getFirst();
    assertThat(req.authorization()).isEqualTo("Bearer secret-key");
    assertThat(req.body()).contains("\"model\":\"test-model\"", "json_object", "SYS");
  }

  @Test
  void mapsProviderFailures() {
    LlmGateway g = gateway("k", 30, 100);
    server.mode(Mode.RATE_LIMITED);
    assertThat(failureOf(g)).isEqualTo(LlmFailure.RATE_LIMITED);
    server.mode(Mode.SERVER_ERROR);
    assertThat(failureOf(g)).isEqualTo(LlmFailure.PROVIDER_ERROR);
    server.mode(Mode.EMPTY);
    assertThat(failureOf(g)).isEqualTo(LlmFailure.EMPTY_RESPONSE);
    server.mode(Mode.SLOW);
    assertThat(failureOf(g)).isEqualTo(LlmFailure.TIMEOUT);
  }

  @Test
  void notConfiguredWithoutKey() {
    LlmGateway g = gateway("", 30, 20);
    assertThat(g.isConfigured()).isFalse();
    assertThat(failureOf(g)).isEqualTo(LlmFailure.NOT_CONFIGURED);
    assertThat(server.requests()).isEmpty();
  }

  @Test
  void budgetCapsCallsPerMinute() {
    LlmGateway g = gateway("k", 2, 20);
    g.complete("s", "u");
    g.complete("s", "u");
    assertThat(failureOf(g)).isEqualTo(LlmFailure.BUDGET_EXCEEDED);
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void circuitOpensAfterRepeatedFailures() {
    LlmGateway g = gateway("k", 100, 4);
    server.mode(Mode.SERVER_ERROR);
    for (int i = 0; i < 4; i++) {
      failureOf(g);
    }
    server.mode(Mode.OK);
    assertThat(failureOf(g)).isEqualTo(LlmFailure.CIRCUIT_OPEN);
    assertThat(server.requests()).hasSize(4);
  }

  @Test
  void keyIsMaskedInToString() {
    var props = new LlmProperties("groq", "m", "u", "super-secret", null, 1, null);
    assertThat(props.toString()).doesNotContain("super-secret").contains("***");
  }
}
