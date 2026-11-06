import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PricePoint } from '../../../core/api/api.models';
import { PriceHistoryChartComponent } from './price-history-chart.component';

describe('PriceHistoryChartComponent', () => {
  let fixture: ComponentFixture<PriceHistoryChartComponent>;
  let el: HTMLElement;

  const render = (points: PricePoint[], current = 30.24): void => {
    fixture.componentRef.setInput('points', points);
    fixture.componentRef.setInput('currentPrice', current);
    fixture.componentRef.setInput('now', new Date('2026-11-06T12:00:00Z'));
    fixture.detectChanges();
  };

  beforeAll(() => registerLocaleData(localeEnIn));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PriceHistoryChartComponent] });
    fixture = TestBed.createComponent(PriceHistoryChartComponent);
    el = fixture.nativeElement as HTMLElement;
  });

  it('shows an empty state without accepted changes', () => {
    render([], 24.99);
    expect(el.querySelector('svg')).toBeNull();
    expect(el.textContent).toContain('No accepted price changes yet');
  });

  it('draws a step line with one focusable marker per change and a table view', () => {
    render([
      { at: '2026-11-06T10:00:00Z', price: 27.49, suggestionId: 1 },
      { at: '2026-11-06T11:00:00Z', price: 30.24, suggestionId: 4 },
    ]);
    const path = el.querySelector('path.line')?.getAttribute('d') ?? '';
    expect(path).toMatch(/^M [\d.]+ [\d.]+ H [\d.]+ V [\d.]+ H [\d.]+$/);
    expect(el.querySelectorAll('g.mark[tabindex="0"]').length).toBe(2);
    expect(el.querySelectorAll('tbody tr').length).toBe(2);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toContain('2 accepted changes');
  });

  it('shows a tooltip on focus', () => {
    render([{ at: '2026-11-06T10:00:00Z', price: 27.49, suggestionId: 1 }]);
    (el.querySelector('g.mark') as SVGGElement).dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(el.querySelector('.tooltip')?.textContent).toContain('₹27.49');
  });
});
