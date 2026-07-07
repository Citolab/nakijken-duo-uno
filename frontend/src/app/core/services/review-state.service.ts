import { Injectable, computed, signal } from '@angular/core';
import { SortingMode, UniqueResponse } from '../models/review.models';

@Injectable({ providedIn: 'root' })
export class ReviewStateService {
  readonly sortingMode = signal<SortingMode>('scan');
  readonly hideConfirmed = signal(false);
  readonly showHighlights = signal(true);
  readonly nakijkOptiesOpen = signal(false);

  readonly localScores = signal<Record<string, number>>({});
  readonly confirmedIds = signal<Set<string>>(new Set());
  readonly reloadGeneration = signal(0);
  private readonly confirmedCountByItem = signal<Record<string, number>>({});

  readonly globalConfirmedCount = computed(() =>
    Object.values(this.confirmedCountByItem()).reduce((total, count) => total + count, 0),
  );

  setSortingMode(mode: SortingMode): void {
    this.sortingMode.set(mode);
  }

  setItemConfirmedCount(itemId: string, confirmed: number): void {
    this.confirmedCountByItem.update((counts) => ({ ...counts, [itemId]: confirmed }));
  }

  setLocalScore(responseId: string, score: number): void {
    this.localScores.update((scores) => ({ ...scores, [responseId]: score }));
  }

  markConfirmed(responseId: string): void {
    this.confirmedIds.update((ids) => new Set([...ids, responseId]));
  }

  resetGrading(): void {
    this.localScores.set({});
    this.confirmedIds.set(new Set());
    this.confirmedCountByItem.set({});
  }

  requestReload(): void {
    this.reloadGeneration.update((value) => value + 1);
  }

  isConfirmed(response: Pick<UniqueResponse, 'id' | 'confirmed'>): boolean {
    return this.confirmedIds().has(response.id) || Boolean(response.confirmed);
  }

  effectiveScore(
    response: Pick<UniqueResponse, 'id' | 'teacherScore' | 'effectiveAiScore' | 'scoreAI'>,
  ): number | null {
    const local = this.localScores()[response.id];
    if (local !== undefined) return local;
    if (response.teacherScore != null) return response.teacherScore;
    if (response.effectiveAiScore != null) return response.effectiveAiScore;
    return response.scoreAI ?? null;
  }
}
