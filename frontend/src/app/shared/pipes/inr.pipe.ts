import { Pipe, PipeTransform } from '@angular/core';
import { formatInr } from '../util/inr';

@Pipe({ name: 'inr' })
export class InrPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatInr(value);
  }
}
