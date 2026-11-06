import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { StrategySettings } from '../api/api.models';
import { AdminApi } from '../api/admin.api';
import { ApiError } from '../errors/api-error';
import { NotificationService } from '../notify/notification.service';

/** FE-06: switch pricing/reorder strategies at runtime (ADR-3). Affects the next suggestion. */
@Component({
  selector: 'sp-strategy-switch',
  template: `
    @if (settings(); as s) {
      <label>
        <span class="muted">Pricing</span>
        <select class="sp-input" [value]="s.pricing" (change)="update('pricing', $event)" aria-label="Pricing strategy">
          @for (k of s.available.pricing; track k) { <option [value]="k">{{ k }}</option> }
        </select>
      </label>
      <label>
        <span class="muted">Reorder</span>
        <select class="sp-input" [value]="s.reorder" (change)="update('reorder', $event)" aria-label="Reorder strategy">
          @for (k of s.available.reorder; track k) { <option [value]="k">{{ k }}</option> }
        </select>
      </label>
    }
  `,
  styles: `
    :host { display: flex; gap: var(--sp-3); align-items: center; }
    label { display: inline-flex; gap: var(--sp-1); align-items: center; font-size: var(--sp-text-xs); }
    select { height: 28px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StrategySwitchComponent implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly notify = inject(NotificationService);
  protected readonly settings = signal<StrategySettings | null>(null);

  ngOnInit(): void {
    this.api.getStrategies().subscribe({
      next: (s) => this.settings.set(s),
      error: () => this.settings.set(null),
    });
  }

  protected update(kind: 'pricing' | 'reorder', event: Event): void {
    const key = (event.target as HTMLSelectElement).value;
    this.api.updateStrategies({ [kind]: key }).subscribe({
      next: (s) => {
        this.settings.set(s);
        this.notify.info(`${kind === 'pricing' ? 'Pricing' : 'Reorder'} strategy: ${key}`);
      },
      error: (e: ApiError) => this.notify.error(e),
    });
  }
}
