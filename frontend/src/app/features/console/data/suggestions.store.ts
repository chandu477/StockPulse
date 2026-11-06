import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, concat, finalize, forkJoin, map, tap, timer, toArray } from 'rxjs';
import {
  Decision,
  PricingSuggestion,
  ReorderSuggestion,
} from '../../../core/api/api.models';
import { SuggestionsApi } from '../../../core/api/suggestions.api';
import { ApiError, toApiError } from '../../../core/errors/api-error';
import { PollingService } from '../../../core/polling/polling.service';
import { ProductsStore } from '../../products/data/products.store';

export type SuggestionKind = 'pricing' | 'reorder';

export interface ProductReviewGroup {
  productId: string;
  productName: string;
  sku: string;
  pricing: PricingSuggestion[];
  reorder: ReorderSuggestion[];
  latest: string;
}

interface PendingSnapshot {
  pricing: PricingSuggestion[];
  reorder: ReorderSuggestion[];
}

/** Pending suggestions for the review queue, grouped per product. */
@Injectable({ providedIn: 'root' })
export class SuggestionsStore {
  private readonly api = inject(SuggestionsApi);
  private readonly polling = inject(PollingService);
  private readonly products = inject(ProductsStore);

  private readonly _pricing = signal<readonly PricingSuggestion[]>([]);
  private readonly _reorder = signal<readonly ReorderSuggestion[]>([]);
  private readonly _loaded = signal(false);
  private readonly _error = signal<ApiError | null>(null);
  private readonly _lastUpdated = signal<Date | null>(null);
  private readonly _busy = signal<ReadonlySet<string>>(new Set());

  readonly loaded = this._loaded.asReadonly();
  readonly error = this._error.asReadonly();
  readonly lastUpdated = this._lastUpdated.asReadonly();
  readonly busy = this._busy.asReadonly();

  readonly pendingCount = computed(() => this._pricing().length + this._reorder().length);

  readonly groups = computed<ProductReviewGroup[]>(() => {
    const groups = new Map<string, ProductReviewGroup>();
    const groupFor = (s: PricingSuggestion | ReorderSuggestion): ProductReviewGroup => {
      let g = groups.get(s.productId);
      if (!g) {
        g = { productId: s.productId, productName: s.productName, sku: s.sku, pricing: [], reorder: [], latest: s.createdAt };
        groups.set(s.productId, g);
      }
      if (s.createdAt > g.latest) {
        g.latest = s.createdAt;
      }
      return g;
    };
    this._pricing().forEach((s) => groupFor(s).pricing.push(s));
    this._reorder().forEach((s) => groupFor(s).reorder.push(s));
    return [...groups.values()].sort((a, b) => b.latest.localeCompare(a.latest));
  });

  startPolling(destroyRef: DestroyRef): void {
    this.polling
      .poll(() => this.fetch())
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((event) => {
        if (event.kind === 'value') {
          this.setAll(event.value);
        } else {
          this._error.set(toApiError(event.error));
          this._loaded.set(true);
        }
      });
  }

  refresh(): void {
    this.fetch().subscribe({
      next: (snapshot) => this.setAll(snapshot),
      error: (e: unknown) => this._error.set(toApiError(e)),
    });
  }

  /** The loop is async: check again shortly after a stock change instead of waiting a full poll. */
  refreshSoon(delayMs = 1200): void {
    timer(delayMs).subscribe(() => this.refresh());
  }

  busyKey(kind: SuggestionKind, id: number): string {
    return `${kind}-${id}`;
  }

  decide(kind: SuggestionKind, id: number, decision: Decision): Observable<PricingSuggestion | ReorderSuggestion> {
    const key = this.busyKey(kind, id);
    this._busy.update((s) => new Set(s).add(key));
    const request: Observable<PricingSuggestion | ReorderSuggestion> =
      kind === 'pricing' ? this.api.decidePricing(id, decision) : this.api.decideReorder(id, decision);
    return request.pipe(
      tap(() => this.afterChange()),
      finalize(() =>
        this._busy.update((s) => {
          const next = new Set(s);
          next.delete(key);
          return next;
        }),
      ),
    );
  }

  /**
   * Accept or reject the whole recommendation: price first, then restock (sequential, so a
   * failure stops before the second call and the error names what is still pending).
   */
  decideAll(group: ProductReviewGroup, decision: Decision): Observable<unknown[]> {
    const key = `all-${group.productId}`;
    const calls: Observable<unknown>[] = [
      ...group.pricing.map((s) => this.api.decidePricing(s.id, decision)),
      ...group.reorder.map((s) => this.api.decideReorder(s.id, decision)),
    ];
    this.markBusy(key, true);
    return concat(...calls).pipe(
      toArray(),
      finalize(() => {
        this.markBusy(key, false);
        this.afterChange();
      }),
    );
  }

  /** Fresh price + restock from the active strategy; supersedes the pending recommendation. */
  recalculate(productId: string): Observable<[PricingSuggestion, ReorderSuggestion]> {
    const key = `recalc-${productId}`;
    this.markBusy(key, true);
    return forkJoin([this.api.suggestPricing(productId), this.api.suggestReorder(productId)]).pipe(
      finalize(() => {
        this.markBusy(key, false);
        this.afterChange();
      }),
    );
  }

  isBusyKey(key: string): boolean {
    return this._busy().has(key);
  }

  private markBusy(key: string, on: boolean): void {
    this._busy.update((s) => {
      const next = new Set(s);
      if (on) {
        next.add(key);
      } else {
        next.delete(key);
      }
      return next;
    });
  }

  private afterChange(): void {
    this.refresh();
    this.products.refresh();
  }

  private fetch(): Observable<PendingSnapshot> {
    return forkJoin({ pricing: this.api.listPricing(), reorder: this.api.listReorder() }).pipe(
      map((s) => ({ pricing: s.pricing, reorder: s.reorder })),
    );
  }

  private setAll(snapshot: PendingSnapshot): void {
    this._pricing.set(snapshot.pricing);
    this._reorder.set(snapshot.reorder);
    this._error.set(null);
    this._loaded.set(true);
    this._lastUpdated.set(new Date());
  }
}
