import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { CATEGORIES, Category, PRODUCT_STATUSES, PricePoint, Product, ProductStatus } from '../../../core/api/api.models';
import { ApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state.component';
import { InrPipe } from '../../../shared/pipes/inr.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { SuggestionsStore } from '../../console/data/suggestions.store';
import { PriceHistoryChartComponent } from '../components/price-history-chart.component';
import { ProductsStore } from '../data/products.store';

const STATUS_LABELS: Record<ProductStatus, string> = {
  ACTIVE: 'Active',
  PRICE_REVIEW_PENDING: 'Review pending',
  OUT_OF_STOCK: 'Out of stock',
};

/** Catalog with demo controls: simulate a sale, set stock, request a suggestion. */
@Component({
  selector: 'sp-catalog-page',
  imports: [DecimalPipe, InrPipe, MatButton, MatTooltip, EmptyStateComponent, ErrorStateComponent, RelativeTimePipe, PriceHistoryChartComponent],
  template: `
    <header class="page-head">
      <div>
        <h1>Catalog</h1>
        <p class="muted">Simulate sales and stock changes. Crossing a threshold queues suggestions automatically.</p>
      </div>
      <div class="filters">
        <label>
          <span class="visually-hidden">Category</span>
          <select class="sp-input" [value]="store.category() ?? ''" (change)="setCategory($event)">
            <option value="">All categories</option>
            @for (c of categories; track c) { <option [value]="c">{{ titleCase(c) }}</option> }
          </select>
        </label>
        <label>
          <span class="visually-hidden">Status</span>
          <select class="sp-input" [value]="store.status() ?? ''" (change)="setStatus($event)">
            <option value="">All statuses</option>
            @for (s of statuses; track s) { <option [value]="s">{{ statusLabel[s] }}</option> }
          </select>
        </label>
        @if (store.lastUpdated(); as at) { <span class="muted updated">Updated {{ at | relativeTime }}</span> }
      </div>
    </header>

    @if (store.error(); as error) {
      <sp-error-state [error]="error" (retry)="store.refresh()" />
    }

    @if (store.loaded()) {
      @if (store.filtered().length === 0 && !store.error()) {
        <sp-empty-state message="No products match these filters." />
      } @else {
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col" class="r">Price</th>
                <th scope="col" class="r">Margin</th>
                <th scope="col" class="r">Stock / threshold</th>
                <th scope="col" class="r">Velocity</th>
                <th scope="col">Status</th>
                <th scope="col" class="r">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (p of store.filtered(); track p.id) {
                @let busy = store.busy().has(p.id);
                @let low = p.stockLevel < p.reorderThreshold;
                <tr>
                  <td>
                    <div class="name">{{ p.name }}</div>
                    <div class="mono muted">{{ p.sku }} · {{ titleCase(p.category) }}</div>
                  </td>
                  <td class="r num">
                    {{ p.currentPrice | inr }}
                    <button type="button" class="link" (click)="toggleHistory(p)" [attr.aria-expanded]="expanded() === p.id" [attr.aria-controls]="'history-' + p.id">History</button>
                  </td>
                  <td class="r num" [matTooltip]="p.costPrice !== null ? 'Cost ' + (p.costPrice | inr) : 'Cost unknown'">
                    @if (p.marginPct !== null) { {{ p.marginPct | number: '1.0-0' }}% } @else { <span class="muted">—</span> }
                  </td>
                  <td class="r num">
                    <span [class.low]="low" [matTooltip]="low ? 'Below reorder threshold' : ''">{{ p.stockLevel }}</span>
                    <span class="muted"> / {{ p.reorderThreshold }}</span>
                    <span class="heat" aria-hidden="true"><span [attr.data-level]="heatLevel(p)" [style.width.%]="heatWidth(p)"></span></span>
                  </td>
                  <td class="r num">{{ p.demandVelocity }}</td>
                  <td>
                    <span class="status" [attr.data-status]="p.status">{{ statusLabel[p.status] }}</span>
                    @if (p.pendingPricingCount + p.pendingReorderCount > 0) {
                      <span class="muted pending">{{ p.pendingPricingCount + p.pendingReorderCount }} pending</span>
                    }
                  </td>
                  <td class="r actions">
                    <button mat-flat-button type="button" [disabled]="busy || p.stockLevel === 0" (click)="sell(p)" [attr.aria-label]="'Sell 1 unit of ' + p.name">Sell 1</button>
                    <span class="stock-edit">
                      <label class="visually-hidden" [attr.for]="'stock-' + p.id">New stock for {{ p.name }}</label>
                      <input #stockInput class="sp-input num" type="number" min="0" [id]="'stock-' + p.id" [value]="p.stockLevel" />
                      <button mat-stroked-button type="button" [disabled]="busy" (click)="setStock(p, stockInput.value)">Set</button>
                    </span>
                    <button mat-button type="button" (click)="recalculate(p)" [disabled]="suggestions.busy().has('recalc-' + p.id)" [attr.aria-label]="'Get price and restock recommendation for ' + p.name">↻ Recommend</button>
                  </td>
                </tr>
                @if (expanded() === p.id) {
                  <tr class="detail" [id]="'history-' + p.id">
                    <td colspan="7">
                      @if (history(); as h) {
                        <sp-price-history-chart [points]="h" [currentPrice]="p.currentPrice" />
                      } @else {
                        <span class="muted">Loading history…</span>
                      }
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .page-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--sp-3); margin-bottom: var(--sp-6); }
    .page-head p { margin: var(--sp-1) 0 0; font-size: var(--sp-text-sm); }
    .filters { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); }
    .updated { font-size: var(--sp-text-xs); }
    .table-wrap { overflow-x: auto; border: 1px solid var(--sp-border); border-radius: var(--sp-radius); }
    table { width: 100%; border-collapse: collapse; font-size: var(--sp-text-sm); }
    th { padding: var(--sp-2) var(--sp-3); background: var(--sp-surface); color: var(--sp-text-muted); font-size: var(--sp-text-xs); font-weight: 500; text-transform: uppercase; letter-spacing: 0.04em; text-align: left; white-space: nowrap; border-bottom: 1px solid var(--sp-border); }
    td { padding: var(--sp-2) var(--sp-3); border-bottom: 1px solid var(--sp-border); vertical-align: middle; }
    tr:last-child td { border-bottom: 0; }
    .r { text-align: right; }
    .name { font-weight: 500; }
    .low { color: var(--sp-low); font-weight: 600; }
    .status { font-size: var(--sp-text-xs); font-weight: 500; white-space: nowrap; }
    [data-status='ACTIVE'] { color: var(--sp-up); }
    .table-wrap { background: var(--sp-bg); }
    [data-status='PRICE_REVIEW_PENDING'] { color: var(--sp-low); }
    [data-status='OUT_OF_STOCK'] { color: var(--sp-down); }
    .pending { display: block; font-size: var(--sp-text-xs); }
    .actions { white-space: nowrap; }
    .actions > * { margin-left: var(--sp-1); }
    .stock-edit { display: inline-flex; gap: var(--sp-1); align-items: center; }
    .stock-edit input { width: 72px; }
    .link { margin-left: var(--sp-1); padding: 0; border: 0; background: none; color: var(--sp-text-muted); font: inherit; font-size: var(--sp-text-xs); text-decoration: underline; cursor: pointer; }
    .heat { display: block; width: 64px; height: 4px; margin: 4px 0 0 auto; border-radius: 2px; background: var(--sp-border); overflow: hidden; }
    .heat > span { display: block; height: 100%; background: var(--sp-text-muted); }
    .heat > [data-level='low'] { background: var(--sp-low); }
    .heat > [data-level='out'] { background: var(--sp-down); }
    .detail td { background: var(--sp-surface); padding: var(--sp-4); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  protected readonly store = inject(ProductsStore);
  protected readonly suggestions = inject(SuggestionsStore);
  private readonly notify = inject(NotificationService);

  protected readonly categories = CATEGORIES;
  protected readonly statuses = PRODUCT_STATUSES;
  protected readonly statusLabel = STATUS_LABELS;
  protected readonly expanded = signal<string | null>(null);
  protected readonly history = signal<readonly PricePoint[] | null>(null);

  protected toggleHistory(p: Product): void {
    if (this.expanded() === p.id) {
      this.expanded.set(null);
      return;
    }
    this.expanded.set(p.id);
    this.history.set(null);
    this.store.priceHistory(p.id).subscribe({
      next: (points) => this.history.set(points),
      error: (e: ApiError) => {
        this.history.set([]);
        this.notify.error(e);
      },
    });
  }

  /** Stock "heat": fill relative to the restock target (3x threshold); colour only when low/out. */
  protected heatWidth(p: Product): number {
    const target = Math.max(p.reorderThreshold * 3, 1);
    return Math.min(100, (p.stockLevel / target) * 100);
  }

  protected heatLevel(p: Product): 'out' | 'low' | 'ok' {
    if (p.stockLevel === 0) {
      return 'out';
    }
    return p.stockLevel < p.reorderThreshold ? 'low' : 'ok';
  }

  protected titleCase(value: string): string {
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  protected setCategory(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.store.category.set(value ? (value as Category) : null);
  }

  protected setStatus(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.store.status.set(value ? (value as ProductStatus) : null);
  }

  protected sell(p: Product): void {
    this.store.sell(p.id).subscribe({
      next: (updated) => {
        this.notify.info(`Sold 1 × ${p.name}. Stock ${updated.stockLevel}.`);
        this.suggestions.refreshSoon();
      },
      error: (e: ApiError) => this.notify.error(e),
    });
  }

  protected setStock(p: Product, raw: string): void {
    const level = Number(raw);
    if (!Number.isInteger(level) || level < 0) {
      this.notify.info('Stock must be a whole number of 0 or more.');
      return;
    }
    this.store.setStock(p.id, level).subscribe({
      next: () => {
        this.notify.info(`${p.name}: stock set to ${level}.`);
        this.suggestions.refreshSoon();
      },
      error: (e: ApiError) => this.notify.error(e),
    });
  }

  /** One recommendation (price + restock) recalculated now; it replaces any pending one. */
  protected recalculate(p: Product): void {
    this.suggestions.recalculate(p.id).subscribe({
      next: () => this.notify.info(`Recommendation ready for ${p.name} — see Recommendations.`),
      error: (e: ApiError) => this.notify.error(e),
    });
  }
}
