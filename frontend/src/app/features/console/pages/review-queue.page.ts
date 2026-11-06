import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Decision } from '../../../core/api/api.models';
import { ApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { formatInr } from '../../../shared/util/inr';
import { ProductsStore } from '../../products/data/products.store';
import { LineDecision, RecommendationCardComponent } from '../components/recommendation-card.component';
import { ProductReviewGroup, SuggestionsStore } from '../data/suggestions.store';

/** The merchandiser's checkpoint: one recommendation (price + restock) per product. */
@Component({
  selector: 'sp-review-queue-page',
  imports: [RouterLink, EmptyStateComponent, ErrorStateComponent, RelativeTimePipe, RecommendationCardComponent],
  template: `
    <header class="page-head">
      <div>
        <h1>Recommendations</h1>
        <p class="muted">One recommendation per product: new price and restock quantity together. They appear automatically when stock or demand changes. Nothing changes until you accept.</p>
      </div>
      <span class="muted updated" aria-live="polite">
        {{ store.groups().length }} product{{ store.groups().length === 1 ? '' : 's' }} to review
        @if (store.lastUpdated(); as at) { · updated {{ at | relativeTime }} }
      </span>
    </header>

    @if (store.error(); as error) {
      <sp-error-state [error]="error" (retry)="store.refresh()" />
    }

    @if (store.loaded()) {
      <div class="list">
        @for (group of store.groups(); track group.productId) {
          <sp-recommendation-card
            [group]="group"
            [product]="products.byId().get(group.productId)"
            [busy]="store.busy()"
            (decideLine)="decideLine($event)"
            (decideAll)="decideAll(group, $event)"
            (recalculate)="recalculate(group)" />
        } @empty {
          @if (!store.error()) {
            <sp-empty-state message="Nothing to review. Recommendations appear here automatically — try “Sell 1” on a low-stock product in the Catalog." />
            <p class="hint"><a routerLink="/products">Go to Catalog →</a></p>
          }
        }
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .page-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--sp-3); margin-bottom: var(--sp-6); }
    .page-head p { margin: var(--sp-1) 0 0; max-width: 640px; font-size: var(--sp-text-sm); }
    .updated { font-size: var(--sp-text-xs); }
    .list { display: flex; flex-direction: column; gap: var(--sp-4); }
    .hint { text-align: center; font-size: var(--sp-text-sm); }
    .hint a { color: var(--sp-accent); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewQueuePage {
  protected readonly store = inject(SuggestionsStore);
  protected readonly products = inject(ProductsStore);
  private readonly notify = inject(NotificationService);

  protected decideLine(e: LineDecision): void {
    const name = e.suggestion.productName;
    const done =
      e.kind === 'pricing'
        ? `${name}: price ${e.decision === 'ACCEPTED' ? 'set to ' + formatInr(e.suggestion.recommendedPrice) : 'suggestion rejected'}`
        : `${name}: restock ${e.decision === 'ACCEPTED' ? '+' + e.suggestion.recommendedQuantity + ' units received' : 'suggestion rejected'}`;
    this.store.decide(e.kind, e.suggestion.id, e.decision).subscribe({
      next: () => this.notify.info(done),
      error: (err: ApiError) => this.onError(err),
    });
  }

  protected decideAll(group: ProductReviewGroup, decision: Decision): void {
    const price = group.pricing[0];
    const restock = group.reorder[0];
    const parts = [
      price ? `price ${formatInr(price.recommendedPrice)}` : null,
      restock ? `+${restock.recommendedQuantity} units` : null,
    ].filter((x): x is string => x !== null);
    this.store.decideAll(group, decision).subscribe({
      next: () =>
        this.notify.info(
          decision === 'ACCEPTED'
            ? `${group.productName}: accepted ${parts.join(' and ')}`
            : `${group.productName}: recommendation rejected`,
        ),
      error: (err: ApiError) => this.onError(err),
    });
  }

  protected recalculate(group: ProductReviewGroup): void {
    this.store.recalculate(group.productId).subscribe({
      next: ([price, restock]) =>
        this.notify.info(
          `${group.productName}: recalculated — ${formatInr(price.recommendedPrice)} and +${restock.recommendedQuantity} units`,
        ),
      error: (err: ApiError) => this.onError(err),
    });
  }

  private onError(e: ApiError): void {
    if (e.code === 'NOT_PENDING') {
      this.notify.info('Already decided elsewhere — refreshed.');
      this.store.refresh();
      return;
    }
    this.notify.error(e);
  }
}
