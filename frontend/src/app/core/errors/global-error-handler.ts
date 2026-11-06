import { ErrorHandler, Injectable, NgZone, inject } from '@angular/core';
import { NotificationService } from '../notify/notification.service';
import { toApiError } from './api-error';

/** Last line of defence: shows a toast instead of a blank screen. No frontend log file (rule 8). */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifications = inject(NotificationService);
  private readonly zone = inject(NgZone);

  handleError(error: unknown): void {
    console.error(error);
    const apiError = toApiError(error);
    this.zone.run(() => this.notifications.error(apiError));
  }
}
