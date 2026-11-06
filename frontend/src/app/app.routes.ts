import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Review · StockPulse',
    loadComponent: () => import('./features/console/pages/review-queue.page').then((m) => m.ReviewQueuePage),
  },
  {
    path: 'products',
    title: 'Catalog · StockPulse',
    loadComponent: () => import('./features/products/pages/catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: '**',
    title: 'Not found · StockPulse',
    loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
  },
];
