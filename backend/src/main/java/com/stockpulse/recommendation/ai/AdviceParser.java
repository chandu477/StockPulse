package com.stockpulse.recommendation.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.FallbackReason;
import org.springframework.stereotype.Component;

/** Tolerant extraction, strict parsing: strip code fences/prose, take the outermost {...}. */
@Component
public class AdviceParser {
  private final ObjectMapper mapper;

  public AdviceParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public JsonNode parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new AiAdviceException(FallbackReason.EMPTY_RESPONSE, "Empty model output");
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new AiAdviceException(FallbackReason.INVALID_JSON, "No JSON object in model output");
    }
    try {
      JsonNode node = mapper.readTree(raw.substring(start, end + 1));
      if (node == null || !node.isObject()) {
        throw new AiAdviceException(FallbackReason.INVALID_JSON, "Model output is not an object");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new AiAdviceException(FallbackReason.INVALID_JSON, "Unparseable model JSON", e);
    }
  }
}
