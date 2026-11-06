import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Decision } from '../../../core/api/api.models';
import { pricing, product, reorder } from '../../../testing/fixtures';
import { ProductReviewGroup } from '../data/suggestions.store';
import { LineDecision, RecommendationCardComponent } from './recommendation-card.component';

describe('RecommendationCardComponent', () => {
  let fixture: ComponentFixture<RecommendationCardComponent>;
  let el: HTMLElement;

  const group = (overrides: Partial<ProductReviewGroup> = {}): ProductReviewGroup => ({
    productId: 'PRD-003',
    productName: 'Organic Cotton T-Shirt',
    sku: 'SKU-TOP-002',
    pricing: [pricing({ currentPrice: 799, recommendedPrice: 879, changePct: 10.01 })],
    reorder: [reorder({ currentStock: 7, recommendedQuantity: 129 })],
    latest: '2026-11-06T10:00:01Z',
    ...overrides,
  });

  const render = (g = group(), busy: ReadonlySet<string> = new Set()): void => {
    fixture.componentRef.setInput('group', g);
    fixture.componentRef.setInput('product', product({ currentPrice: 799, costPrice: 360, marginPct: 54.94 }));
    fixture.componentRef.setInput('busy', busy);
    fixture.detectChanges();
  };

  const button = (label: string): HTMLButtonElement =>
    el.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;

  beforeAll(() => registerLocaleData(localeEnIn));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RecommendationCardComponent] });
    fixture = TestBed.createComponent(RecommendationCardComponent);
    el = fixture.nativeElement as HTMLElement;
  });

  it('shows price and restock together in rupees with margin and one trigger badge', () => {
    render();
    const text = el.textContent ?? '';
    expect(text).toContain('₹799 →');
    expect(text).toContain('₹879');
    expect(text).toContain('▲ 10.0%');
    expect(text).toContain('Margin 55% → 59%');
    expect(text).toContain('+129 units');
    expect(text).toContain('Stock 7 → 136 on arrival');
    expect(el.querySelectorAll('sp-trigger-badge').length).toBe(1);
    expect(text).toContain('Low stock');
  });

  it('renders AI reasoning as text, never as HTML', () => {
    render(group({ pricing: [pricing({ reasoning: '<img src=x onerror=alert(1)>' })] }));
    expect(el.querySelector('.reasoning img')).toBeNull();
    expect(el.querySelector('.reasoning')?.textContent).toContain('<img');
  });

  it('emits per-line decisions, accept/reject all and recalculate', () => {
    const lines: LineDecision[] = [];
    const all: Decision[] = [];
    let recalcs = 0;
    fixture.componentInstance.decideLine.subscribe((d: LineDecision) => lines.push(d));
    fixture.componentInstance.decideAll.subscribe((d: Decision) => all.push(d));
    fixture.componentInstance.recalculate.subscribe(() => recalcs++);
    render();

    button('Accept price for Organic Cotton T-Shirt').click();
    button('Reject restock for Organic Cotton T-Shirt').click();
    button('Accept all for Organic Cotton T-Shirt').click();
    button('Recalculate recommendation for Organic Cotton T-Shirt').click();

    expect(lines.map((l) => `${l.kind}:${l.decision}`)).toEqual(['pricing:ACCEPTED', 'reorder:REJECTED']);
    expect(all).toEqual(['ACCEPTED']);
    expect(recalcs).toBe(1);
  });

  it('disables everything while the card is recalculating', () => {
    render(group(), new Set(['recalc-PRD-003']));
    expect(button('Accept all for Organic Cotton T-Shirt').disabled).toBeTrue();
    expect(button('Accept price for Organic Cotton T-Shirt').disabled).toBeTrue();
  });

  it('shows only the remaining line after one part was decided', () => {
    render(group({ pricing: [] }));
    expect(el.textContent).not.toContain('Accept price');
    expect(el.textContent).toContain('+129 units');
  });
});
