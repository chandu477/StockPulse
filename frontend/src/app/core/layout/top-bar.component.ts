import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SuggestionsStore } from '../../features/console/data/suggestions.store';
import { StrategySwitchComponent } from './strategy-switch.component';

@Component({
  selector: 'sp-top-bar',
  imports: [RouterLink, RouterLinkActive, StrategySwitchComponent],
  template: `
    <header class="bar">
      <a class="brand" routerLink="/"><span class="logo" aria-hidden="true"></span>StockPulse<span class="persona">Apparel supplier · ₹</span></a>
      <nav aria-label="Main">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
          Recommendations
          @if (suggestions.groups().length > 0) { <span class="count num">{{ suggestions.groups().length }}</span> }
        </a>
        <a routerLink="/products" routerLinkActive="active">Catalog</a>
      </nav>
      <sp-strategy-switch class="switch" />
    </header>
  `,
  styles: `
    .bar { display: flex; align-items: center; gap: var(--sp-6); height: 48px; padding: 0 var(--sp-6); border-bottom: 1px solid var(--sp-border); background: var(--sp-bg); }
    .brand { display: inline-flex; align-items: center; gap: var(--sp-2); font-weight: 600; color: var(--sp-text); text-decoration: none; letter-spacing: -0.01em; }
    .logo { width: 14px; height: 14px; border-radius: 4px; background: var(--sp-accent); }
    .persona { padding: 1px var(--sp-2); border-radius: 999px; background: var(--sp-accent-soft); color: var(--sp-accent); font-size: var(--sp-text-xs); font-weight: 500; }
    nav { display: flex; gap: var(--sp-4); }
    nav a { display: inline-flex; align-items: center; gap: var(--sp-1); height: 48px; color: var(--sp-text-muted); text-decoration: none; font-size: var(--sp-text-sm); border-bottom: 2px solid transparent; }
    nav a.active { color: var(--sp-accent); border-bottom-color: var(--sp-accent); }
    .count { min-width: 18px; padding: 0 5px; border-radius: 9px; background: var(--sp-accent); color: var(--sp-accent-fg); font-size: 11px; text-align: center; }
    .switch { margin-left: auto; }
    @media (max-width: 600px) { .bar { padding: 0 var(--sp-4); gap: var(--sp-4); } .switch { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopBarComponent {
  protected readonly suggestions = inject(SuggestionsStore);
}
