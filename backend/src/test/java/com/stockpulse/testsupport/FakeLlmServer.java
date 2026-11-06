package com.stockpulse.testsupport;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal OpenAI-compatible LLM stub for tests (QA-03 style failure injection without Docker).
 * Answers pricing or reorder JSON depending on the prompt, or injects a failure mode.
 */
public final class FakeLlmServer implements AutoCloseable {
  public enum Mode {
    OK,
    GARBAGE,
    FENCED,
    RATE_LIMITED,
    SERVER_ERROR,
    ABSURD_PRICE,
    HOLD_WITH_CHANGE,
    FRACTIONAL_QUANTITY,
    EMPTY,
    SLOW
  }

  public record Recorded(String body, String authorization) {}

  private final HttpServer server;
  private volatile Mode mode = Mode.OK;
  private volatile String pricingJson =
      "{\"recommendedPrice\": %s, \"direction\": \"INCREASE\", \"confidence\": 0.82,"
          + " \"reasoning\": \"AI: protect scarce stock with a modest increase.\"}";
  private final List<Recorded> requests = new CopyOnWriteArrayList<>();

  public FakeLlmServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public void mode(Mode mode) {
    this.mode = mode;
  }

  public List<Recorded> requests() {
    return requests;
  }

  private void handle(com.sun.net.httpserver.HttpExchange ex) throws IOException {
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(new Recorded(body, ex.getRequestHeaders().getFirst("Authorization")));
    boolean reorder = body.contains("recommendedQuantity");
    Mode m = mode;
    int status = 200;
    String content;
    switch (m) {
      case RATE_LIMITED -> {
        status = 429;
        content = null;
      }
      case SERVER_ERROR -> {
        status = 503;
        content = null;
      }
      case GARBAGE -> content = "Sure! I think you should raise the price a bit.";
      case EMPTY -> content = "";
      case SLOW -> {
        sleep(3000);
        content = ok(reorder, body);
      }
      case FENCED -> content = "Here you go:\n```json\n" + ok(reorder, body) + "\n```";
      case ABSURD_PRICE ->
          content =
              reorder
                  ? ok(true, body)
                  : "{\"recommendedPrice\": 999999, \"direction\": \"INCREASE\","
                      + " \"confidence\": 0.9, \"reasoning\": \"x\"}";
      case HOLD_WITH_CHANGE ->
          content =
              reorder
                  ? ok(true, body)
                  : "{\"recommendedPrice\": 1.01, \"direction\": \"HOLD\", \"confidence\": 0.9,"
                      + " \"reasoning\": \"x\"}";
      case FRACTIONAL_QUANTITY ->
          content =
              reorder
                  ? "{\"recommendedQuantity\": 1.5, \"confidence\": 0.5, \"reasoning\": \"x\"}"
                  : ok(false, body);
      default -> content = ok(reorder, body);
    }
    String response =
        content == null
            ? "{\"error\":{\"message\":\"injected\"}}"
            : "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + quote(content)
                + "}}],\"usage\":{\"total_tokens\":42}}";
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }

  /** Valid advice: +5% on the prompt's current price, or 40 units. */
  private String ok(boolean reorder, String body) {
    if (reorder) {
      return "{\"recommendedQuantity\": 40, \"leadTimeDays\": 5, \"confidence\": 0.74,"
          + " \"reasoning\": \"AI: cover lead-time demand plus a buffer.\"}";
    }
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("current price: ([0-9.]+)").matcher(body);
    double price = m.find() ? Double.parseDouble(m.group(1)) : 10.0;
    return pricingJson.formatted(String.format(java.util.Locale.ROOT, "%.2f", price * 1.05));
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
