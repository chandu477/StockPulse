import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { FallbackReason, SuggestionSource } from '../../core/api/api.models';

const REASONS: Record<FallbackReason, string> = {
  TIMEOUT: 'the AI provider timed out',
  RATE_LIMITED: 'the AI provider rate-limited the request',
  PROVIDER_ERROR: 'the AI provider returned an error',
  EMPTY_RESPONSE: 'the AI returned an empty response',
  INVALID_JSON: 'the AI response was not valid JSON',
  OUT_OF_BOUNDS: 'the AI answer failed validation',
  CIRCUIT_OPEN: 'AI calls are paused after repeated failures',
  BUDGET_EXCEEDED: 'the AI call budget was exceeded',
  NOT_CONFIGURED: 'no AI provider key is configured',
};

/** Where the advice came from: AI, rule, or rule fallback (with the reason). */
@Component({
  selector: 'sp-source-chip',
  imports: [MatTooltip],
  template: `<span class="chip" [class.fallback]="source() === 'RULE_FALLBACK'" [matTooltip]="tooltip()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 0 var(--sp-2);
      border: 1px solid var(--sp-border-strong);
      border-radius: var(--sp-radius-sm);
      font-size: var(--sp-text-xs);
      color: var(--sp-text-muted);
      white-space: nowrap;
    }
    .fallback {
      color: var(--sp-fallback);
      border-color: color-mix(in srgb, var(--sp-fallback) 40%, transparent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceChipComponent {
  readonly source = input.required<SuggestionSource>();
  readonly fallbackReason = input<FallbackReason | null>(null);

  protected readonly label = computed(() => {
    switch (this.source()) {
      case 'AI':
        return 'AI';
      case 'RULE':
        return 'Rule';
      default:
        return `Fallback · ${this.fallbackReason() ?? 'unknown'}`;
    }
  });

  protected readonly tooltip = computed(() => {
    const reason = this.fallbackReason();
    if (this.source() === 'RULE_FALLBACK') {
      return `Rule-based result because ${reason ? REASONS[reason] : 'the AI failed'}. Nothing was dropped.`;
    }
    return this.source() === 'AI' ? 'Recommended by the AI advisor' : 'Recommended by the rule strategy';
  });
}
