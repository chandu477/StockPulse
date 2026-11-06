import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TriggerReason } from '../../core/api/api.models';

const LABELS: Record<TriggerReason, string> = {
  INVENTORY_LOW: 'Low stock',
  DEMAND_SPIKE: 'Demand spike',
  MANUAL: 'Manual',
  INITIAL: 'Initial',
};

/** Makes the agentic loop visible: why this suggestion exists. Text, never colour alone. */
@Component({
  selector: 'sp-trigger-badge',
  template: `<span class="badge" [attr.data-trigger]="trigger()" [attr.title]="title()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 1px var(--sp-2);
      border-radius: var(--sp-radius-sm);
      font-size: var(--sp-text-xs);
      font-weight: 500;
      white-space: nowrap;
      color: var(--sp-manual);
      background: color-mix(in srgb, var(--sp-manual) 8%, transparent);
    }
    [data-trigger='INVENTORY_LOW'] {
      color: var(--sp-low);
      background: color-mix(in srgb, var(--sp-low) 10%, transparent);
    }
    [data-trigger='DEMAND_SPIKE'] {
      color: var(--sp-spike);
      background: color-mix(in srgb, var(--sp-spike) 10%, transparent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TriggerBadgeComponent {
  readonly trigger = input.required<TriggerReason>();
  protected readonly label = computed(() => LABELS[this.trigger()]);
  protected readonly title = computed(() =>
    this.trigger() === 'MANUAL' ? 'Requested by a merchandiser' : 'Created automatically by the agentic loop',
  );
}
