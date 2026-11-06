import { Pipe, PipeTransform } from '@angular/core';

/** "just now", "12s ago", "3m ago", "2h ago". Input: ISO string or Date. */
@Pipe({ name: 'relativeTime' })
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | Date | null | undefined, now: Date | null = null): string {
    if (!value) {
      return '';
    }
    const at = typeof value === 'string' ? new Date(value) : value;
    const seconds = Math.max(0, Math.round(((now ?? new Date()).getTime() - at.getTime()) / 1000));
    if (seconds < 5) {
      return 'just now';
    }
    if (seconds < 60) {
      return `${seconds}s ago`;
    }
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) {
      return `${minutes}m ago`;
    }
    return `${Math.round(minutes / 60)}h ago`;
  }
}
