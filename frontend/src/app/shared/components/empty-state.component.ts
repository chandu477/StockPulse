import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'sp-empty-state',
  template: `<p class="empty">{{ message() }}</p>`,
  styles: `
    .empty {
      margin: 0;
      padding: var(--sp-8) var(--sp-4);
      text-align: center;
      color: var(--sp-text-muted);
      border: 1px dashed var(--sp-border-strong);
      border-radius: var(--sp-radius);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly message = input.required<string>();
}
