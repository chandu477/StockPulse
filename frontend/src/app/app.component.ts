import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject } from '@angular/core';
import { MatProgressBar } from '@angular/material/progress-bar';
import { RouterOutlet } from '@angular/router';
import { TopBarComponent } from './core/layout/top-bar.component';
import { SuggestionsStore } from './features/console/data/suggestions.store';
import { ProductsStore } from './features/products/data/products.store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TopBarComponent, MatProgressBar],
  template: `
    <sp-top-bar />
    <div class="progress">
      @if (loading()) { <mat-progress-bar mode="indeterminate" aria-label="Loading" /> }
    </div>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .progress { height: 2px; }
    main { max-width: 1200px; margin: 0 auto; padding: var(--sp-6); }
    @media (max-width: 600px) { main { padding: var(--sp-4); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent implements OnInit {
  private readonly products = inject(ProductsStore);
  private readonly suggestions = inject(SuggestionsStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = computed(() => !this.products.loaded() || !this.suggestions.loaded());

  ngOnInit(): void {
    // App-wide polling (5 s, paused when the tab is hidden): the top bar count and both pages share it.
    this.products.startPolling(this.destroyRef);
    this.suggestions.startPolling(this.destroyRef);
  }
}
