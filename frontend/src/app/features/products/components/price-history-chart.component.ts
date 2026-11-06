import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { PricePoint } from '../../../core/api/api.models';
import { InrPipe } from '../../../shared/pipes/inr.pipe';
import { formatInr } from '../../../shared/util/inr';

interface Mark {
  x: number;
  y: number;
  price: number;
  at: Date;
  label: string;
  suggestionId: number | null;
}

const W = 560;
const H = 120;
const PAD = { top: 12, right: 12, bottom: 22, left: 52 };

/**
 * Accepted price changes over time as a step line (price holds until the next accepted change).
 * Single series: no legend; ink-coloured 2px line, 8px markers with a surface ring, hover/focus
 * tooltip per change, and a table view below for screen readers and exact values.
 */
@Component({
  selector: 'sp-price-history-chart',
  imports: [DatePipe, InrPipe],
  template: `
    <figure class="figure">
      <figcaption class="title">Price history <span class="muted">· accepted changes only</span></figcaption>
      @if (marks().length === 0) {
        <p class="muted empty">No accepted price changes yet. Current price {{ currentPrice() | inr }}.</p>
      } @else {
        <div class="plot">
          <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" [attr.aria-label]="summary()" preserveAspectRatio="none">
            <line class="grid" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="scale().yMin" [attr.y2]="scale().yMin" />
            <line class="grid" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="scale().yMax" [attr.y2]="scale().yMax" />
            <text class="axis" [attr.x]="pad.left - 8" [attr.y]="scale().yMax + 4" text-anchor="end">{{ scale().maxLabel }}</text>
            <text class="axis" [attr.x]="pad.left - 8" [attr.y]="scale().yMin + 4" text-anchor="end">{{ scale().minLabel }}</text>
            <text class="axis" [attr.x]="pad.left" [attr.y]="height - 4">{{ scale().startLabel }}</text>
            <text class="axis" [attr.x]="width - pad.right" [attr.y]="height - 4" text-anchor="end">now</text>
            <path class="line" [attr.d]="path()" />
            @for (m of marks(); track $index) {
              <g class="mark" tabindex="0" [attr.aria-label]="m.label"
                 (mouseenter)="active.set($index)" (mouseleave)="active.set(null)"
                 (focus)="active.set($index)" (blur)="active.set(null)">
                <circle class="hit" [attr.cx]="m.x" [attr.cy]="m.y" r="12" />
                <circle class="dot" [attr.cx]="m.x" [attr.cy]="m.y" r="4" />
              </g>
            }
          </svg>
          @if (activeMark(); as m) {
            <div class="tooltip" role="status" [style.left.%]="(m.x / width) * 100" [style.top.%]="(m.y / height) * 100">
              <strong class="num">{{ m.price | inr }}</strong>
              <span class="muted">{{ m.at | date: 'MMM d, HH:mm' }}{{ m.suggestionId ? ' · #' + m.suggestionId : '' }}</span>
            </div>
          }
        </div>
        <table class="table">
          <caption class="visually-hidden">Accepted price changes</caption>
          <thead><tr><th scope="col">When</th><th scope="col" class="r">Price</th><th scope="col" class="r">Suggestion</th></tr></thead>
          <tbody>
            @for (p of points(); track p.suggestionId) {
              <tr><td>{{ p.at | date: 'MMM d, HH:mm:ss' }}</td><td class="r num">{{ p.price | inr }}</td><td class="r mono">#{{ p.suggestionId }}</td></tr>
            }
          </tbody>
        </table>
      }
    </figure>
  `,
  styles: `
    .figure { margin: 0; }
    .title { font-size: var(--sp-text-sm); font-weight: 600; margin-bottom: var(--sp-2); }
    .empty { margin: 0; font-size: var(--sp-text-sm); }
    .plot { position: relative; max-width: 560px; }
    svg { display: block; width: 100%; height: 120px; overflow: visible; }
    .grid { stroke: var(--sp-border); stroke-width: 1; }
    .axis { fill: var(--sp-text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
    .line { fill: none; stroke: var(--sp-accent); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; vector-effect: non-scaling-stroke; }
    .dot { fill: var(--sp-accent); stroke: var(--sp-bg); stroke-width: 2; vector-effect: non-scaling-stroke; }
    .hit { fill: transparent; }
    .mark { cursor: default; outline: none; }
    .mark:focus-visible .dot { stroke: var(--sp-focus); }
    .tooltip { position: absolute; transform: translate(-50%, calc(-100% - 10px)); display: flex; flex-direction: column; padding: var(--sp-1) var(--sp-2); background: var(--sp-bg); border: 1px solid var(--sp-border-strong); border-radius: var(--sp-radius-sm); font-size: var(--sp-text-xs); white-space: nowrap; pointer-events: none; }
    .table { margin-top: var(--sp-2); border-collapse: collapse; font-size: var(--sp-text-xs); }
    .table th, .table td { padding: 2px var(--sp-3) 2px 0; text-align: left; border: 0; background: none; color: var(--sp-text-muted); font-weight: 400; text-transform: none; letter-spacing: 0; }
    .r { text-align: right; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceHistoryChartComponent {
  readonly points = input.required<readonly PricePoint[]>();
  readonly currentPrice = input.required<number>();
  /** Injected for tests; defaults to the render time. */
  readonly now = input<Date | null>(null);

  protected readonly width = W;
  protected readonly height = H;
  protected readonly pad = PAD;
  protected readonly active = signal<number | null>(null);

  protected readonly scale = computed(() => {
    const pts = this.points();
    const now = (this.now() ?? new Date()).getTime();
    const prices = [...pts.map((p) => p.price), this.currentPrice()];
    const lo = Math.min(...prices);
    const hi = Math.max(...prices);
    const span = hi - lo || hi * 0.1 || 1;
    const yLo = lo - span * 0.15;
    const yHi = hi + span * 0.15;
    const t0 = pts.length ? new Date(pts[0].at).getTime() : now;
    const tSpan = Math.max(now - t0, 1);
    const x = (t: number) => PAD.left + ((t - t0) / tSpan) * (W - PAD.left - PAD.right);
    const y = (v: number) => PAD.top + (1 - (v - yLo) / (yHi - yLo)) * (H - PAD.top - PAD.bottom);
    const fmt = (v: number) => formatInr(v);
    return {
      x,
      y,
      now,
      yMin: y(lo),
      yMax: y(hi),
      minLabel: fmt(lo),
      maxLabel: fmt(hi),
      startLabel: pts.length ? new Date(t0).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '',
    };
  });

  protected readonly marks = computed<Mark[]>(() => {
    const s = this.scale();
    return this.points().map((p) => {
      const at = new Date(p.at);
      return {
        x: s.x(at.getTime()),
        y: s.y(p.price),
        price: p.price,
        at,
        suggestionId: p.suggestionId,
        label: `Price set to ${formatInr(p.price)} at ${at.toLocaleString()}`,
      };
    });
  });

  /** Step path: horizontal until the next change, then vertical; extends to "now". */
  protected readonly path = computed(() => {
    const m = this.marks();
    if (m.length === 0) {
      return '';
    }
    const s = this.scale();
    let d = `M ${m[0].x} ${m[0].y}`;
    for (let i = 1; i < m.length; i++) {
      d += ` H ${m[i].x} V ${m[i].y}`;
    }
    return `${d} H ${s.x(s.now)}`;
  });

  protected readonly activeMark = computed(() => {
    const i = this.active();
    return i === null ? null : (this.marks()[i] ?? null);
  });

  protected readonly summary = computed(() => {
    const pts = this.points();
    return `Price history: ${pts.length} accepted change${pts.length === 1 ? '' : 's'}, now ${formatInr(this.currentPrice())}`;
  });
}
