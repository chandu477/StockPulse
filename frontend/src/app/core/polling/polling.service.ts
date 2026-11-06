import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import {
  EMPTY,
  Observable,
  catchError,
  defer,
  distinctUntilChanged,
  exhaustMap,
  filter,
  fromEvent,
  map,
  of,
  startWith,
  switchMap,
  timer,
} from 'rxjs';

export type PollEvent<T> = { kind: 'value'; value: T } | { kind: 'error'; error: unknown };

/** Max ticks skipped after repeated failures (5 s interval -> backoff caps at 60 s). */
const MAX_SKIPPED_TICKS = 11;

/**
 * ADR-8 polling: fires immediately, then every `intervalMs`; pauses while the tab is hidden;
 * backs off exponentially on errors (10 s, 20 s, 40 s, 60 s) and resets on success.
 */
@Injectable({ providedIn: 'root' })
export class PollingService {
  private readonly document = inject(DOCUMENT);

  private readonly visible$ = fromEvent(this.document, 'visibilitychange').pipe(
    map(() => !this.document.hidden),
    startWith(!this.document.hidden),
    distinctUntilChanged(),
  );

  poll<T>(source: () => Observable<T>, intervalMs = 5000): Observable<PollEvent<T>> {
    return this.visible$.pipe(switchMap((visible) => (visible ? this.loop(source, intervalMs) : EMPTY)));
  }

  private loop<T>(source: () => Observable<T>, intervalMs: number): Observable<PollEvent<T>> {
    return defer(() => {
      let failures = 0;
      let skip = 0;
      return timer(0, intervalMs).pipe(
        filter(() => {
          if (skip > 0) {
            skip--;
            return false;
          }
          return true;
        }),
        exhaustMap(() =>
          source().pipe(
            map((value): PollEvent<T> => {
              failures = 0;
              return { kind: 'value', value };
            }),
            catchError((error: unknown) => {
              failures++;
              skip = Math.min(2 ** failures - 1, MAX_SKIPPED_TICKS);
              return of<PollEvent<T>>({ kind: 'error', error });
            }),
          ),
        ),
      );
    });
  }
}
