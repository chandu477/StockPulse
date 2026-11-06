package com.stockpulse.recommendation.ai;

import com.stockpulse.entity.FallbackReason;
import com.stockpulse.integration.llm.LlmException;
import com.stockpulse.integration.llm.LlmGateway;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.ReorderAdvice;
import org.springframework.stereotype.Component;

/** Pure AI path: prompt -> LLM -> parse -> validate. Any failure throws AiAdviceException. */
@Component
public class AiAdvisor {
  private final LlmGateway gateway;
  private final PromptLibrary prompts;
  private final AdviceParser parser;
  private final AdviceValidator validator;

  public AiAdvisor(
      LlmGateway gateway, PromptLibrary prompts, AdviceParser parser, AdviceValidator validator) {
    this.gateway = gateway;
    this.prompts = prompts;
    this.parser = parser;
    this.validator = validator;
  }

  public boolean isConfigured() {
    return gateway.isConfigured();
  }

  public PricingAdvice pricing(CommerceContext ctx) {
    return validator.pricing(parser.parse(call(prompts.pricing(ctx))), ctx);
  }

  public ReorderAdvice reorder(CommerceContext ctx) {
    return validator.reorder(parser.parse(call(prompts.reorder(ctx))));
  }

  private String call(String userPrompt) {
    try {
      return gateway.complete(prompts.system(), userPrompt);
    } catch (LlmException e) {
      throw new AiAdviceException(FallbackReason.valueOf(e.failure().name()), e.getMessage(), e);
    }
  }
}
