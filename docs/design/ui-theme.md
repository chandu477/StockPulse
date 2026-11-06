# StockPulse UI Theme: White & Blue

Principle: **a calm, white interface with one blue accent; the data does the talking.** White cards on a very light blue page, blue for primary actions, links, active navigation, chart lines and confidence. Other colour only for meaning (triggers, price direction, status). 1 px borders with a hairline shadow on cards, one font (Inter), prices in **Indian rupees** (`en-IN` grouping, e.g. ₹1,08,999). Components use `--sp-*` tokens only; the source of truth is `frontend/src/styles/_tokens.scss`.

## Tokens (`frontend/src/styles/_tokens.scss`)

Key values (light): page `#f5f8fe`, card `#ffffff`, border `#e2e8f0`, text `#0f172a`, muted `#64748b`, **accent `#2563eb`** (hover `#1d4ed8`, soft `#eff5ff`). Dark: page `#0b1220`, accent `#60a5fa`. The block below is the original minimal set, kept for reference; the file is authoritative.

```scss
:root {
  /* Surface & text */
  --sp-bg:            #ffffff;
  --sp-surface:       #fafafa;   /* page sections, table header */
  --sp-border:        #e5e5e5;
  --sp-border-strong: #d4d4d4;
  --sp-text:          #171717;
  --sp-text-muted:    #737373;
  --sp-text-faint:    #a3a3a3;

  /* The only accent: primary actions, focus ring, links */
  --sp-accent:        #171717;   /* near-black buttons = minimal */
  --sp-accent-fg:     #ffffff;
  --sp-focus:         #2563eb;

  /* Meaning (text colour + 8% tint background) */
  --sp-up:            #15803d;   /* INCREASE, accept, ACTIVE */
  --sp-down:          #b91c1c;   /* DECREASE, reject, OUT_OF_STOCK */
  --sp-hold:          #737373;   /* HOLD */
  --sp-low:           #b45309;   /* INVENTORY_LOW, PRICE_REVIEW_PENDING */
  --sp-spike:         #6d28d9;   /* DEMAND_SPIKE */
  --sp-manual:        #525252;   /* MANUAL / INITIAL */
  --sp-fallback:      #b45309;   /* Rule fallback chip */

  /* Type */
  --sp-font:          'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;
  --sp-font-mono:     'JetBrains Mono', ui-monospace, monospace;  /* SKU, Error ref only */
  --sp-text-xs: 12px; --sp-text-sm: 13px; --sp-text-md: 14px; --sp-text-lg: 16px; --sp-text-xl: 20px;

  /* Space (4 px scale), shape, motion */
  --sp-1: 4px; --sp-2: 8px; --sp-3: 12px; --sp-4: 16px; --sp-6: 24px; --sp-8: 32px;
  --sp-radius: 6px; --sp-radius-sm: 4px;
  --sp-ease: 120ms ease-out;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) {
    --sp-bg: #0a0a0a; --sp-surface: #141414; --sp-border: #262626; --sp-border-strong: #333333;
    --sp-text: #fafafa; --sp-text-muted: #a3a3a3; --sp-text-faint: #737373;
    --sp-accent: #fafafa; --sp-accent-fg: #0a0a0a;
    --sp-up: #4ade80; --sp-down: #f87171; --sp-low: #fbbf24; --sp-spike: #a78bfa; --sp-fallback: #fbbf24;
  }
}
```

Tints: `color-mix(in srgb, var(--sp-low) 8%, transparent)` for badge backgrounds.

## Angular Material 19
Use `@include mat.theme((color: (theme-type: light, primary: mat.$azure-palette), typography: Inter, density: -2));` and then map `--mat-sys-*` to `--sp-*` overrides in `styles.scss`. Use only `mat-button` (flat/stroked), `mat-table`, `mat-select`, `mat-snack-bar`, `mat-tooltip`, `mat-progress-bar`. **Avoid** elevation, ripples (`MAT_RIPPLE_GLOBAL_OPTIONS: {disabled: true}`), FABs and cards with shadows.

## Layout
- Top bar of 48 px: the "StockPulse" wordmark (600 weight), nav (Review · Catalog), strategy toggle on the right. A 1 px bottom border, no background colour.
- Content max width 1200 px, 24 px gutters (16 px below 600 px). No sidebar.
- Tables: 40 px rows, `tabular-nums`, right-aligned numbers, header on `--sp-surface` with muted 12 px uppercase labels.

## Components
| Component | Spec |
|---|---|
| Trigger badge | 12 px text, 2 px × 8 px padding, `--sp-radius-sm`, coloured text on an 8% tint. Labels: `Low stock`, `Demand spike`, `Manual` |
| Source chip | Outline only: `AI` · `Rule` · `Fallback` (with the reason in a tooltip) |
| Direction | `▲ 10.0%` in `--sp-up`, `▼` in `--sp-down`, `— Hold` muted |
| Confidence | 48 px × 4 px bar plus a `82%` label; the bar is `--sp-text` at 80% opacity, never coloured |
| Recommendation card | One per product: header (name, SKU, trigger badge), facts row (stock / threshold, velocity, price, margin), two light-blue panels (Price, Restock) each with source chip, confidence, reasoning and its own Accept / Reject; footer: ↻ Recalculate, Reject all, **Accept all** (filled blue) |
| Reasoning | `--sp-text-muted`, 13 px, clamped to 2 lines, "More" toggle |
| Empty state | One muted line ("Nothing to review. Suggestions appear here automatically.") |
| Error | Inline `--sp-down` text + `Error ref: abcd1234` in mono, with a Retry text button |
| Loading | A 2 px indeterminate progress bar under the top bar. No spinners over content |

## Accessibility
Contrast ≥ 4.5:1 for all text tokens on `--sp-bg`; a visible 2 px `--sp-focus` outline; colour is never the only signal (badges have text and direction has an arrow); respect `prefers-reduced-motion`.
