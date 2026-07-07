import { Routes } from '@angular/router';
import { ReviewPageComponent } from './features/review/review.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'review' },
  {
    path: 'review',
    component: ReviewPageComponent,
  },
  { path: '**', redirectTo: 'review' },
];
