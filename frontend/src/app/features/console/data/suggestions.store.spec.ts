import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { errorInterceptor } from '../../../core/errors/error.interceptor';
import { ApiError } from '../../../core/errors/api-error';
import { pricing, product, reorder } from '../../../testing/fixtures';
import { SuggestionsStore } from './suggestions.store';

describe('SuggestionsStore', () => {
  let store: SuggestionsStore;
  let http: HttpTestingController;

  const flushLists = (p = [pricing()], r = [reorder()]): void => {
    http.expectOne('/api/v1/pricing-suggestions?status=PENDING').flush(p);
    http.expectOne('/api/v1/reorder-suggestions?status=PENDING').flush(r);
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    store = TestBed.inject(SuggestionsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('groups pending suggestions by product, newest product first', () => {
    store.refresh();
    flushLists(
      [pricing(), pricing({ id: 2, productId: 'PRD-008', productName: 'Hoodie', createdAt: '2026-11-06T11:00:00Z', triggerReason: 'DEMAND_SPIKE' })],
      [reorder()],
    );

    const groups = store.groups();
    expect(groups.map((g) => g.productId)).toEqual(['PRD-008', 'PRD-003']);
    expect(groups[1].pricing.length).toBe(1);
    expect(groups[1].reorder.length).toBe(1);
    expect(store.pendingCount()).toBe(3);
    expect(store.loaded()).toBeTrue();
  });

  it('accepting sends the decision, marks busy, then refreshes suggestions and products', () => {
    let done = false;
    store.decide('pricing', 1, 'ACCEPTED').subscribe(() => (done = true));
    expect(store.busy().has('pricing-1')).toBeTrue();

    const req = http.expectOne('/api/v1/pricing-suggestions/1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'ACCEPTED', note: null });
    req.flush(pricing({ status: 'ACCEPTED' }));

    flushLists([], [reorder()]);
    http.expectOne('/api/v1/products').flush([product({ currentPrice: 27.49 })]);
    expect(done).toBeTrue();
    expect(store.busy().size).toBe(0);
    expect(store.pendingCount()).toBe(1);
  });

  it('surfaces the typed error and clears busy on failure', () => {
    let error: ApiError | undefined;
    store.decide('reorder', 5, 'REJECTED').subscribe({ error: (e: ApiError) => (error = e) });
    http.expectOne('/api/v1/reorder-suggestions/5').flush(
      { timestamp: 't', status: 409, error: 'Conflict', code: 'NOT_PENDING', message: 'already', path: '/x', traceId: 'abc12345xyz', errors: [] },
      { status: 409, statusText: 'Conflict' },
    );
    expect(error?.code).toBe('NOT_PENDING');
    expect(store.busy().size).toBe(0);
  });

  it('accept all decides price first, then restock, then refreshes', () => {
    store.refresh();
    flushLists();
    const group = store.groups()[0];
    let done = false;
    store.decideAll(group, 'ACCEPTED').subscribe(() => (done = true));
    expect(store.busy().has('all-PRD-003')).toBeTrue();

    http.expectOne('/api/v1/pricing-suggestions/1').flush(pricing({ status: 'ACCEPTED' }));
    const restock = http.expectOne('/api/v1/reorder-suggestions/1');
    expect(restock.request.body).toEqual({ status: 'ACCEPTED', note: null });
    restock.flush(reorder({ status: 'ACCEPTED' }));

    expect(done).toBeTrue();
    expect(store.busy().size).toBe(0);
    flushLists([], []);
    http.expectOne('/api/v1/products').flush([product()]);
    expect(store.groups().length).toBe(0);
  });

  it('recalculate asks for price and restock together and refreshes', () => {
    let result: unknown;
    store.recalculate('PRD-003').subscribe((r) => (result = r));
    expect(store.busy().has('recalc-PRD-003')).toBeTrue();
    http.expectOne({ method: 'POST', url: '/api/v1/products/PRD-003/suggest-pricing' }).flush(pricing({ id: 9, triggerReason: 'MANUAL' }));
    http.expectOne({ method: 'POST', url: '/api/v1/products/PRD-003/suggest-reorder' }).flush(reorder({ id: 9, triggerReason: 'MANUAL' }));
    expect(result).toBeTruthy();
    flushLists([pricing({ id: 9, triggerReason: 'MANUAL' })], [reorder({ id: 9, triggerReason: 'MANUAL' })]);
    http.expectOne('/api/v1/products').flush([product()]);
    expect(store.groups()[0].pricing[0].triggerReason).toBe('MANUAL');
  });

  it('records a load error without dropping previous data', () => {
    store.refresh();
    flushLists();
    store.refresh();
    http.expectOne('/api/v1/pricing-suggestions?status=PENDING').flush(null, { status: 0, statusText: 'Unknown' });
    http.expectOne('/api/v1/reorder-suggestions?status=PENDING');
    expect(store.error()?.code).toBe('NETWORK_ERROR');
    expect(store.pendingCount()).toBe(2);
  });
});
