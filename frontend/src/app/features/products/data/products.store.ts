import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize, tap } from 'rxjs';
import { Category, PricePoint, Product, ProductStatus } from '../../../core/api/api.models';
import { ProductsApi } from '../../../core/api/products.api';
import { ApiError, toApiError } from '../../../core/errors/api-error';
import { PollingService } from '../../../core/polling/polling.service';

/** Single source of truth for the catalog (ADR-8: signals + RxJS, no NgRx). */
@Injectable({ providedIn: 'root' })
export class ProductsStore {
  private readonly api = inject(ProductsApi);
  private readonly polling = inject(PollingService);

  private readonly _products = signal<readonly Product[]>([]);
  private readonly _loaded = signal(false);
  private readonly _error = signal<ApiError | null>(null);
  private readonly _lastUpdated = signal<Date | null>(null);
  private readonly _busy = signal<ReadonlySet<string>>(new Set());

  readonly category = signal<Category | null>(null);
  readonly status = signal<ProductStatus | null>(null);

  readonly loaded = this._loaded.asReadonly();
  readonly error = this._error.asReadonly();
  readonly lastUpdated = this._lastUpdated.asReadonly();
  readonly products = this._products.asReadonly();

  readonly filtered = computed(() => {
    const category = this.category();
    const status = this.status();
    return this._products().filter(
      (p) => (!category || p.category === category) && (!status || p.status === status),
    );
  });

  readonly byId = computed(() => new Map(this._products().map((p) => [p.id, p])));

  startPolling(destroyRef: DestroyRef): void {
    this.polling
      .poll(() => this.api.list())
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
    this.api.list().subscribe({
      next: (products) => this.setAll(products),
      error: (e: unknown) => this._error.set(toApiError(e)),
    });
  }

  isBusy(productId: string): boolean {
    return this._busy().has(productId);
  }

  readonly busy = this._busy.asReadonly();

  priceHistory(productId: string): Observable<PricePoint[]> {
    return this.api.priceHistory(productId);
  }

  sell(productId: string, quantity = 1): Observable<Product> {
    return this.track(productId, this.api.order(productId, quantity));
  }

  setStock(productId: string, stockLevel: number): Observable<Product> {
    return this.track(productId, this.api.setStock(productId, stockLevel));
  }

  private track(productId: string, request: Observable<Product>): Observable<Product> {
    this._busy.update((s) => new Set(s).add(productId));
    return request.pipe(
      tap((updated) => this.upsert(updated)),
      finalize(() =>
        this._busy.update((s) => {
          const next = new Set(s);
          next.delete(productId);
          return next;
        }),
      ),
    );
  }

  private upsert(updated: Product): void {
    this._products.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
  }

  private setAll(products: Product[]): void {
    this._products.set(products);
    this._error.set(null);
    this._loaded.set(true);
    this._lastUpdated.set(new Date());
  }
}
