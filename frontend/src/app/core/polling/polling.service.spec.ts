import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { PollEvent, PollingService } from './polling.service';

describe('PollingService', () => {
  let service: PollingService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PollingService);
  });

  it('fetches immediately and then every interval', fakeAsync(() => {
    let calls = 0;
    const events: PollEvent<number>[] = [];
    const sub = service.poll(() => of(++calls), 5000).subscribe((e) => events.push(e));

    tick(0);
    expect(calls).toBe(1);
    tick(5000);
    expect(calls).toBe(2);
    tick(5000);
    expect(calls).toBe(3);
    expect(events.every((e) => e.kind === 'value')).toBeTrue();

    sub.unsubscribe();
    discardPeriodicTasks();
  }));

  it('backs off after a failure and resets after success', fakeAsync(() => {
    let calls = 0;
    const source = (): Observable<number> => {
      calls++;
      return calls === 1 ? throwError(() => new Error('down')) : of(calls);
    };
    const events: PollEvent<number>[] = [];
    const sub = service.poll(source, 5000).subscribe((e) => events.push(e));

    tick(0);
    expect(events[0].kind).toBe('error');
    tick(5000); // skipped (backoff)
    expect(calls).toBe(1);
    tick(5000); // 10 s after failure
    expect(calls).toBe(2);
    tick(5000); // back to normal cadence
    expect(calls).toBe(3);

    sub.unsubscribe();
    discardPeriodicTasks();
  }));
});
