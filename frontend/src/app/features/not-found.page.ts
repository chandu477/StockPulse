import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'sp-not-found-page',
  imports: [RouterLink],
  template: `<h1>Page not found</h1><p class="muted"><a routerLink="/">Back to the review queue</a></p>`,
  styles: `a { color: var(--sp-text); }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
