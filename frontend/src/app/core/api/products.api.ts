import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { Category, PricePoint, Product, ProductStatus } from './api.models';

export interface ProductFilter {
  status?: ProductStatus | null;
  category?: Category | null;
}

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly http = inject(HttpClient);

  list(filter: ProductFilter = {}): Observable<Product[]> {
    let params = new HttpParams();
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.category) {
      params = params.set('category', filter.category);
    }
    return this.http.get<Product[]>(`${API_BASE_URL}/products`, { params });
  }

  order(productId: string, quantity: number): Observable<Product> {
    return this.http.post<Product>(`${API_BASE_URL}/products/${encodeURIComponent(productId)}/orders`, {
      quantity,
    });
  }

  priceHistory(productId: string): Observable<PricePoint[]> {
    return this.http.get<PricePoint[]>(`${API_BASE_URL}/products/${encodeURIComponent(productId)}/price-history`);
  }

  setStock(productId: string, stockLevel: number): Observable<Product> {
    return this.http.patch<Product>(`${API_BASE_URL}/products/${encodeURIComponent(productId)}/stock`, {
      stockLevel,
    });
  }
}
