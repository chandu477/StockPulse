import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import {
  Decision,
  DecisionRequest,
  PricingSuggestion,
  ReorderSuggestion,
  SuggestionStatus,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class SuggestionsApi {
  private readonly http = inject(HttpClient);

  listPricing(status: SuggestionStatus = 'PENDING'): Observable<PricingSuggestion[]> {
    return this.http.get<PricingSuggestion[]>(`${API_BASE_URL}/pricing-suggestions`, {
      params: new HttpParams().set('status', status),
    });
  }

  listReorder(status: SuggestionStatus = 'PENDING'): Observable<ReorderSuggestion[]> {
    return this.http.get<ReorderSuggestion[]>(`${API_BASE_URL}/reorder-suggestions`, {
      params: new HttpParams().set('status', status),
    });
  }

  decidePricing(id: number, status: Decision, note?: string): Observable<PricingSuggestion> {
    const body: DecisionRequest = { status, note: note ?? null };
    return this.http.patch<PricingSuggestion>(`${API_BASE_URL}/pricing-suggestions/${id}`, body);
  }

  decideReorder(id: number, status: Decision, note?: string): Observable<ReorderSuggestion> {
    const body: DecisionRequest = { status, note: note ?? null };
    return this.http.patch<ReorderSuggestion>(`${API_BASE_URL}/reorder-suggestions/${id}`, body);
  }

  suggestPricing(productId: string): Observable<PricingSuggestion> {
    return this.http.post<PricingSuggestion>(
      `${API_BASE_URL}/products/${encodeURIComponent(productId)}/suggest-pricing`,
      null,
    );
  }

  suggestReorder(productId: string): Observable<ReorderSuggestion> {
    return this.http.post<ReorderSuggestion>(
      `${API_BASE_URL}/products/${encodeURIComponent(productId)}/suggest-reorder`,
      null,
    );
  }
}
