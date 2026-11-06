import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { StrategySettings, StrategyUpdateRequest } from './api.models';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  getStrategies(): Observable<StrategySettings> {
    return this.http.get<StrategySettings>(`${API_BASE_URL}/admin/strategies`);
  }

  updateStrategies(req: StrategyUpdateRequest): Observable<StrategySettings> {
    return this.http.put<StrategySettings>(`${API_BASE_URL}/admin/strategies`, req);
  }
}
