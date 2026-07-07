import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Assessment,
  Delivery,
  ItemStatsResponse,
  ScoringDefinition,
  SortingMode,
  TeacherScoreEntry,
} from '../models/review.models';

@Injectable({ providedIn: 'root' })
export class ReviewApiService {
  private readonly http = inject(HttpClient);

  getAssessment(assessmentId: string): Observable<Assessment> {
    return this.http.get<Assessment>(`/api/assessments/${assessmentId}`);
  }

  getDelivery(deliveryId: string): Observable<Delivery> {
    return this.http.get<Delivery>(`/api/deliveries/${deliveryId}`);
  }

  getItemStats(deliveryId: string, sortingMode: SortingMode): Observable<ItemStatsResponse> {
    return this.http.get<ItemStatsResponse>(
      `/api/deliveries/${deliveryId}/item-stats`,
      { params: { sortingMode } },
    );
  }

  getScoringDefinition(
    assessmentId: string,
    itemIdentifier: string,
  ): Observable<ScoringDefinition> {
    return this.http.get<ScoringDefinition>(
      `/api/assessments/${assessmentId}/items/${itemIdentifier}/scoring-definition`,
    );
  }

  confirmScores(assessmentId: string, responseIds: string[]): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>(
      `/api/assessments/${assessmentId}/responses/confirm-scores`,
      { responseIds },
    );
  }

  setTeacherScores(entries: TeacherScoreEntry[]): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>('/api/aigrading/teacher-score', { entries });
  }

  clearAllScores(assessmentId: string): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>(
      `/api/assessments/${assessmentId}/responses/clear-scores`,
      {},
    );
  }

  fetchItemXml(href: string): Observable<string> {
    return this.http.get(href, { responseType: 'text' });
  }
}
