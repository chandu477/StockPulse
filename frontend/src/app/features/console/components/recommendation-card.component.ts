import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  Decision,
  PricingSuggestion,
  Product,
  ReorderSuggestion,
  TriggerReason,
} from '../../../core/api/api.models';
import { ConfidenceBarComponent } from '../../../shared/components/confidence-bar.component';
import { SourceChipComponent } from '../../../shared/components/source-chip.component';
import { TriggerBadgeComponent } from '../../../shared/components/trigger-badge.component';
import { InrPipe } from '../../../shared/pipes/inr.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { marginPct } from '../../../shared/util/margin';
import { ProductReviewGroup } from '../data/suggestions.store';

export type LineDecision =
  | { kind: 'pricing'; suggestion: PricingSuggestion; decision: Decision }
  | { kind: 'reorder'; suggestion: ReorderSuggestion; decision: Decision };

/**
 * One recommendation per product: the price change and the restock quantity together, each
 * decidable on its own, plus Accept all / Reject all and Recalculate (fresh numbers from the
 * active strategy against current stock and price).
 */
@Component({
  selector: 'sp-recommendation-card',
  imports: [DecimalPipe, MatButton, ConfidenceBarComponent, SourceChipComponent, TriggerBadgeComponent, InrPipe, RelativeTimePipe],
  template: `
    @let g = group();
    @let p = product();
    <article class="card" [attr.aria-label]="'Recommendation for ' + g.productName">
      <header class="head">
        <div class="title">
          <h2>{{ g.productName }}</h2>
          <span class="mono muted">{{ g.sku }}</span>
        </div>
        <div class="badges">
          @for (t of triggers(); track t) { <sp-trigger-badge [trigger]="t" /> }
          <span class="muted when">{{ g.latest | relativeTime }}</span>
        </div>
      </header>

      @if (p) {
        <dl class="facts num">
          <div><dt>Stock</dt><dd><strong [class.low]="p.stockLevel < p.reorderThreshold">{{ p.stockLevel }}</strong> / {{ p.reorderThreshold }}</dd></div>
          <div><dt>Velocity</dt><dd>{{ p.demandVelocity }}/day</dd></div>
          <div><dt>Price</dt><dd>{{ p.currentPrice | inr }}</dd></div>
          @if (p.marginPct !== null) { <div><dt>Margin</dt><dd>{{ p.marginPct | number: '1.0-0' }}%</dd></div> }
        </dl>
      }

      <div class="lines">
        @if (pricing(); as s) {
          <section class="line" aria-label="Price recommendation">
            <div class="line-head">
              <span class="kind">Price</span>
              <sp-source-chip [source]="s.source" [fallbackReason]="s.fallbackReason" />
              <sp-confidence-bar [value]="s.confidence" />
            </div>
            <div class="value num">
              <span class="from">{{ s.currentPrice | inr }} →</span>
              <span>{{ s.recommendedPrice | inr }}</span>
              <span class="delta" [attr.data-direction]="s.direction">
                @switch (s.direction) {
                  @case ('INCREASE') { ▲ {{ s.changePct | number: '1.1-1' }}% }
                  @case ('DECREASE') { ▼ {{ -s.changePct | number: '1.1-1' }}% }
                  @default { — Hold }
                }
              </span>
            </div>
            @if (margin(); as m) { <div class="sub num">Margin {{ m.before | number: '1.0-0' }}% → {{ m.after | number: '1.0-0' }}%</div> }
            <p class="reasoning" [class.clamped]="!priceExpanded()">{{ s.reasoning }}</p>
            <div class="line-actions">
              @if (s.reasoning.length > 140) {
                <button type="button" class="more" (click)="priceExpanded.set(!priceExpanded())">{{ priceExpanded() ? 'Less' : 'More' }}</button>
              }
              <span class="spacer"></span>
              <button mat-button type="button" [disabled]="isBusy('pricing', s.id)" (click)="decideLine.emit({ kind: 'pricing', suggestion: s, decision: 'REJECTED' })" [attr.aria-label]="'Reject price for ' + g.productName">Reject</button>
              <button mat-stroked-button type="button" [disabled]="isBusy('pricing', s.id)" (click)="decideLine.emit({ kind: 'pricing', suggestion: s, decision: 'ACCEPTED' })" [attr.aria-label]="'Accept price for ' + g.productName">Accept price</button>
            </div>
          </section>
        }

        @if (reorder(); as r) {
          <section class="line" aria-label="Restock recommendation">
            <div class="line-head">
              <span class="kind">Restock</span>
              <sp-source-chip [source]="r.source" [fallbackReason]="r.fallbackReason" />
              <sp-confidence-bar [value]="r.confidence" />
            </div>
            <div class="value num">
              <span>+{{ r.recommendedQuantity }} units</span>
              <span class="from">· {{ r.leadTimeDays }}-day lead time</span>
            </div>
            <div class="sub num">Stock {{ r.currentStock }} → {{ r.currentStock + r.recommendedQuantity }} on arrival</div>
            <p class="reasoning" [class.clamped]="!restockExpanded()">{{ r.reasoning }}</p>
            <div class="line-actions">
              @if (r.reasoning.length > 140) {
                <button type="button" class="more" (click)="restockExpanded.set(!restockExpanded())">{{ restockExpanded() ? 'Less' : 'More' }}</button>
              }
              <span class="spacer"></span>
              <button mat-button type="button" [disabled]="isBusy('reorder', r.id)" (click)="decideLine.emit({ kind: 'reorder', suggestion: r, decision: 'REJECTED' })" [attr.aria-label]="'Reject restock for ' + g.productName">Reject</button>
              <button mat-stroked-button type="button" [disabled]="isBusy('reorder', r.id)" (click)="decideLine.emit({ kind: 'reorder', suggestion: r, decision: 'ACCEPTED' })" [attr.aria-label]="'Accept restock for ' + g.productName">Accept restock</button>
            </div>
          </section>
        }
      </div>

      <footer class="foot">
        <button mat-button type="button" [disabled]="cardBusy()" (click)="recalculate.emit()" [attr.aria-label]="'Recalculate recommendation for ' + g.productName">↻ Recalculate</button>
        <span class="spacer"></span>
        <button mat-button type="button" [disabled]="cardBusy()" (click)="decideAll.emit('REJECTED')" [attr.aria-label]="'Reject all for ' + g.productName">Reject all</button>
        <button mat-flat-button type="button" [disabled]="cardBusy()" (click)="decideAll.emit('ACCEPTED')" [attr.aria-label]="'Accept all for ' + g.productName">Accept all</button>
      </footer>
    </article>
  `,
  styles: `
    :host { display: block; }
    .card { display: flex; flex-direction: column; gap: var(--sp-3); padding: var(--sp-4); background: var(--sp-bg); border: 1px solid var(--sp-border); border-radius: var(--sp-radius); box-shadow: var(--sp-shadow); }
    .head { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: var(--sp-2); }
    .title { display: flex; flex-direction: column; }
    h2 { font-size: var(--sp-text-lg); }
    .badges { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); }
    .when { font-size: var(--sp-text-xs); }
    .facts { display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-6); margin: 0; font-size: var(--sp-text-sm); }
    .facts div { display: flex; gap: var(--sp-1); }
    .facts dt { color: var(--sp-text-muted); }
    .facts dd { margin: 0; }
    .low { color: var(--sp-low); }
    .lines { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: var(--sp-3); }
    .line { display: flex; flex-direction: column; gap: var(--sp-2); padding: var(--sp-3); background: var(--sp-accent-soft); border: 1px solid var(--sp-surface-strong); border-radius: var(--sp-radius); }
    .line-head { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); }
    .kind { margin-right: auto; font-size: var(--sp-text-xs); font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; color: var(--sp-accent); }
    .value { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--sp-2); font-size: var(--sp-text-xl); font-weight: 600; }
    .from { font-size: var(--sp-text-lg); font-weight: 400; color: var(--sp-text-muted); }
    .delta { font-size: var(--sp-text-sm); font-weight: 500; color: var(--sp-hold); }
    [data-direction='INCREASE'] { color: var(--sp-up); }
    [data-direction='DECREASE'] { color: var(--sp-down); }
    .sub { font-size: var(--sp-text-xs); color: var(--sp-text-muted); }
    .reasoning { margin: 0; font-size: var(--sp-text-sm); color: var(--sp-text-muted); white-space: pre-line; }
    .clamped { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .line-actions { display: flex; align-items: center; gap: var(--sp-1); margin-top: auto; }
    .more { padding: 0; border: 0; background: none; color: var(--sp-accent); font: inherit; font-size: var(--sp-text-xs); text-decoration: underline; cursor: pointer; }
    .spacer { flex: 1; }
    .foot { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); padding-top: var(--sp-3); border-top: 1px solid var(--sp-border); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecommendationCardComponent {
  readonly group = input.required<ProductReviewGroup>();
  readonly product = input<Product | undefined>(undefined);
  readonly busy = input<ReadonlySet<string>>(new Set());

  readonly decideLine = output<LineDecision>();
  readonly decideAll = output<Decision>();
  readonly recalculate = output<void>();

  protected readonly priceExpanded = signal(false);
  protected readonly restockExpanded = signal(false);

  /** One pending suggestion per type per product (backend guarantee); take the newest. */
  protected readonly pricing = computed(() => this.group().pricing[0] ?? null);
  protected readonly reorder = computed(() => this.group().reorder[0] ?? null);

  protected readonly triggers = computed<TriggerReason[]>(() => {
    const set = new Set<TriggerReason>();
    const p = this.pricing();
    const r = this.reorder();
    if (p) {
      set.add(p.triggerReason);
    }
    if (r) {
      set.add(r.triggerReason);
    }
    return [...set];
  });

  protected readonly margin = computed(() => {
    const s = this.pricing();
    const cost = this.product()?.costPrice ?? null;
    if (!s) {
      return null;
    }
    const before = marginPct(s.currentPrice, cost);
    const after = marginPct(s.recommendedPrice, cost);
    return before === null || after === null ? null : { before, after };
  });

  protected readonly cardBusy = computed(() => {
    const id = this.group().productId;
    const busy = this.busy();
    return busy.has(`all-${id}`) || busy.has(`recalc-${id}`);
  });

  protected isBusy(kind: 'pricing' | 'reorder', id: number): boolean {
    return this.cardBusy() || this.busy().has(`${kind}-${id}`);
  }
}
