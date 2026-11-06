import { TestBed } from '@angular/core/testing';
import { TriggerReason } from '../../core/api/api.models';
import { TriggerBadgeComponent } from './trigger-badge.component';

describe('TriggerBadgeComponent', () => {
  const cases: [TriggerReason, string][] = [
    ['INVENTORY_LOW', 'Low stock'],
    ['DEMAND_SPIKE', 'Demand spike'],
    ['MANUAL', 'Manual'],
  ];

  cases.forEach(([trigger, label]) =>
    it(`renders "${label}" for ${trigger}`, () => {
      const fixture = TestBed.createComponent(TriggerBadgeComponent);
      fixture.componentRef.setInput('trigger', trigger);
      fixture.detectChanges();
      const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge') as HTMLElement;
      expect(badge.textContent?.trim()).toBe(label);
      expect(badge.dataset['trigger']).toBe(trigger);
    }),
  );
});
