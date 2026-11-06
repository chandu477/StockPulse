import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiError } from '../errors/api-error';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  info(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000 });
  }

  error(error: ApiError): void {
    const ref = error.ref ? ` · Error ref: ${error.ref}` : '';
    this.snackBar.open(`${error.message}${ref}`, 'Dismiss', { duration: 6000 });
  }
}
