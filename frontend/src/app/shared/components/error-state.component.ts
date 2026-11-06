import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { ApiError } from '../../core/errors/api-error';

@Component({
  selector: 'sp-error-state',
  imports: [MatButton],
  template: `
    <div class="error" role="alert">
      <span>{{ error().message }}</span>
      @if (error().ref; as ref) {
        <span class="mono muted">Error ref: {{ ref }}</span>
      }
      <button mat-button type="button" (click)="retry.emit()">Retry</button>
    </div>
  `,
  styles: `
    .error {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sp-3);
      padding: var(--sp-3) var(--sp-4);
      border: 1px solid color-mix(in srgb, var(--sp-down) 30%, transparent);
      border-radius: var(--sp-radius);
      color: var(--sp-down);
      font-size: var(--sp-text-sm);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorStateComponent {
  readonly error = input.required<ApiError>();
  readonly retry = output<void>();
}
