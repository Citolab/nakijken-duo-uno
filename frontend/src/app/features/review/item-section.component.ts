import { Component, computed, inject, input, signal, effect } from '@angular/core';
import { catchError, concatMap, of } from 'rxjs';
import { ItemInfo, ItemStatistics, SortingMode, UniqueResponse } from '../../core/models/review.models';
import { ReviewApiService } from '../../core/services/review-api.service';
import { ReviewStateService } from '../../core/services/review-state.service';
import { QtiItemPreviewComponent } from '../../core/qti/qti-item-preview.component';
import { ReviewToolbarComponent } from './review-toolbar.component';
import { ResponseRowComponent } from './response-row.component';

type ClusterGroup = { familyId: string | null; responses: UniqueResponse[] };

@Component({
  selector: 'app-item-section',
  standalone: true,
  imports: [QtiItemPreviewComponent, ReviewToolbarComponent, ResponseRowComponent],
  template: `
    <section class="item-section" [attr.data-item-id]="item().identifier">
      <header class="item-section__header">
        <h2>{{ item().title }}</h2>
      </header>

      <div class="item-section__grid">
        <app-qti-item-preview [href]="item().href" [packageBaseHref]="packageBaseHref()" />

        <div class="item-section__panel">
          <app-review-toolbar
            [totalCount]="totalResponseCount()"
            [confirmedCount]="confirmedCount()"
            [answerModel]="answerModel()"
            [maxScore]="maxScore()"
          />

          <div class="item-section__responses">
            @if (loading()) {
              <p>Vraag wordt geladen...</p>
            } @else {
              @for (group of groupedResponses(); track group.familyId ?? $index) {
                <div class="item-section__cluster" [class.item-section__cluster--multi]="group.responses.length > 1">
                  @for (response of group.responses; track response.id) {
                    <app-response-row
                      [response]="response"
                      [maxScore]="maxScore()"
                      (confirm)="onConfirm($event)"
                      (scoreSelected)="onScoreSelected($event.response, $event.score)"
                    />
                  }
                </div>
              }
            }
          </div>
        </div>
      </div>
    </section>
  `,
  styles: `
    .item-section { margin-bottom: 3rem; }
    .item-section__header h2 { margin: 0 0 1rem; font-size: 1.125rem; }
    .item-section__grid {
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
      gap: 1rem;
      align-items: start;
    }
    @media (max-width: 960px) {
      .item-section__grid { grid-template-columns: 1fr; }
    }
    .item-section__panel {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      align-self: start;
      min-width: 0;
    }
    .item-section__responses { display: flex; flex-direction: column; gap: 0.5rem; }
    .item-section__cluster {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .item-section__cluster--multi {
      border-left: 3px solid var(--uno-color-accent);
      padding-left: 0.25rem;
    }
  `,
})
export class ItemSectionComponent {
  readonly assessmentId = input.required<string>();
  readonly item = input.required<ItemInfo>();
  readonly stats = input.required<ItemStatistics | null>();
  readonly packageBaseHref = input('/demo-package');
  readonly sortingMode = input.required<SortingMode>();

  private readonly api = inject(ReviewApiService);
  private readonly state = inject(ReviewStateService);

  readonly loading = signal(true);
  readonly answerModel = signal<string | undefined>(undefined);
  readonly maxScore = signal(1);

  constructor() {
    effect((onCleanup) => {
      const item = this.item();
      const assessmentId = this.assessmentId();
      this.loading.set(true);

      const subscription = this.api
        .getScoringDefinition(assessmentId, item.identifier)
        .pipe(
          catchError(() =>
            of({ itemIdentifier: item.identifier, answerModel: undefined, maxScore: item.maxScore }),
          ),
        )
        .subscribe((definition) => {
          this.answerModel.set(definition.answerModel);
          this.maxScore.set(Math.round(definition.maxScore ?? item.maxScore ?? 1));
          this.loading.set(false);
        });
      onCleanup(() => subscription.unsubscribe());
    });

    effect(() => {
      this.state.setItemConfirmedCount(this.item().identifier, this.confirmedCount());
    });
  }

  readonly totalResponseCount = computed(() => this.stats()?.responses?.length ?? 0);

  readonly visibleResponses = computed(() => {
    const responses = this.stats()?.responses ?? [];
    const hideConfirmed = this.state.hideConfirmed();
    const confirmedIds = this.state.confirmedIds();
    if (!hideConfirmed) return responses;
    return responses.filter(
      (response) => !confirmedIds.has(response.id) && !response.confirmed,
    );
  });

  readonly confirmedCount = computed(() => {
    const responses = this.stats()?.responses ?? [];
    const confirmedIds = this.state.confirmedIds();
    return responses.filter(
      (response) => confirmedIds.has(response.id) || Boolean(response.confirmed),
    ).length;
  });

  readonly groupedResponses = computed(() => {
    const responses = this.visibleResponses();
    if (this.sortingMode() === 'grading') {
      return [{ familyId: null, responses }];
    }

    const groups: ClusterGroup[] = [];
    for (const response of responses) {
      const familyId = response.familyId ?? null;
      const last = groups[groups.length - 1];
      if (last && last.familyId === familyId) {
        last.responses.push(response);
      } else {
        groups.push({ familyId, responses: [response] });
      }
    }
    return groups;
  });

  onScoreSelected(response: UniqueResponse, score: number): void {
    this.state.setLocalScore(response.id, score);
    const needsConfirm = this.markConfirmedLocally(response);
    // The confirm call must not overtake the score it confirms, so chain the requests.
    this.api
      .setTeacherScores([{ responseId: response.id, teacherScore: score }])
      .pipe(
        concatMap(() =>
          needsConfirm ? this.api.confirmScores(this.assessmentId(), [response.id]) : of(null),
        ),
      )
      .subscribe();
  }

  onConfirm(response: UniqueResponse): void {
    const score = this.state.effectiveScore(response);
    if (score == null) return;
    if (!this.markConfirmedLocally(response)) return;
    this.api.confirmScores(this.assessmentId(), [response.id]).subscribe();
  }

  private markConfirmedLocally(response: UniqueResponse): boolean {
    const confirmedIds = this.state.confirmedIds();
    if (confirmedIds.has(response.id) || response.confirmed) {
      return false;
    }
    this.state.markConfirmed(response.id);
    return true;
  }
}
