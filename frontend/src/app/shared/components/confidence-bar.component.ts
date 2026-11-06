import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'sp-confidence-bar',
  template: `
    <span class="wrap" role="img" [attr.aria-label]="'Confidence ' + percent() + ' percent'">
      <span class="track"><span class="fill" [style.width.%]="percent()"></span></span>
      <span class="value num">{{ percent() }}%</span>
    </span>
  `,
  styles: `
    .wrap {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-2);
      font-size: var(--sp-text-xs);
      color: var(--sp-text-muted);
    }
    .track {
      width: 48px;
      height: 4px;
      border-radius: 2px;
      background: var(--sp-border);
      overflow: hidden;
    }
    .fill {
      display: block;
      height: 100%;
      background: var(--sp-accent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfidenceBarComponent {
  /** 0.0 - 1.0 */
  readonly value = input.required<number>();
  protected readonly percent = computed(() => Math.round(Math.min(1, Math.max(0, this.value())) * 100));
}
