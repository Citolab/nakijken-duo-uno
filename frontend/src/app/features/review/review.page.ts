import { Component, computed, inject, signal, effect } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { ReviewApiService } from '../../core/services/review-api.service';
import { ReviewStateService } from '../../core/services/review-state.service';
import { ItemInfo, ItemStatistics } from '../../core/models/review.models';
import { UnoHeaderComponent } from '../../uno/uno-header.component';
import { ItemSectionComponent } from './item-section.component';
import { NakijkOptiesDialogComponent } from './nakijk-opties-dialog.component';

const DEMO_ASSESSMENT_ID = 'demo';
const DEFAULT_DELIVERY_ID = 'ZSVY';

interface ReviewItemEntry {
  item: ItemInfo;
  stats: ItemStatistics | null;
}

@Component({
  selector: 'app-review-page',
  standalone: true,
  imports: [UnoHeaderComponent, ItemSectionComponent, NakijkOptiesDialogComponent],
  template: `
    <uno-header
      title="Nakijken"
      [subtitle]="assessment()?.name ?? 'Demo afname'"
    >
      <button
        header-actions
        type="button"
        class="review-page__settings"
        aria-label="Nakijk opties"
        (click)="state.nakijkOptiesOpen.set(true)"
      >
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path
            fill="currentColor"
            d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8m9.4 4.6a7.8 7.8 0 0 0 .1-1.2l2-1.6-2-3.4-2.4 1a8.1 8.1 0 0 0-2.1-1.2l-.4-2.6H9.4l-.4 2.6a8.1 8.1 0 0 0-2.1 1.2l-2.4-1-2 3.4 2 1.6a7.8 7.8 0 0 0-.1 1.2c0 .4 0 .8.1 1.2l-2 1.6 2 3.4 2.4-1a8.1 8.1 0 0 0 2.1 1.2l.4 2.6h4.2l.4-2.6a8.1 8.1 0 0 0 2.1-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2"
          />
        </svg>
      </button>
    </uno-header>

    <app-nakijk-opties-dialog
      [open]="state.nakijkOptiesOpen()"
      [confirmedCount]="state.globalConfirmedCount()"
      (close)="state.nakijkOptiesOpen.set(false)"
    />

    <main class="review-page">
      @if (loading()) {
        <p class="review-page__loading">Demo wordt geladen…</p>
      } @else if (error()) {
        <p class="review-page__error">{{ error() }}</p>
      } @else {
        @for (entry of reviewItems(); track entry.item.identifier) {
          <app-item-section
            [assessmentId]="assessmentId()"
            [item]="entry.item"
            [stats]="entry.stats"
            [packageBaseHref]="assessment()?.packageBaseHref ?? '/demo-package'"
            [sortingMode]="state.sortingMode()"
          />
        }
      }
    </main>
  `,
  styles: `
    .review-page {
      max-width: 1400px;
      margin: 0 auto;
      padding: 1.5rem;
    }
    .review-page__loading, .review-page__error {
      padding: 2rem 0;
      color: var(--app-color-muted);
    }
    .review-page__error { color: var(--app-color-danger); }
    .review-page__settings {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.5rem;
      height: 2.5rem;
      border: 1px solid rgb(255 255 255 / 0.35);
      border-radius: 999px;
      background: rgb(255 255 255 / 0.12);
      color: #fff;
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease;
    }
    .review-page__settings:hover {
      background: rgb(255 255 255 / 0.2);
      border-color: rgb(255 255 255 / 0.55);
    }
  `,
})
export class ReviewPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ReviewApiService);
  protected readonly state = inject(ReviewStateService);

  private readonly queryParams = toSignal(
    this.route.queryParamMap.pipe(map((params) => ({
      delivery: params.get('delivery') ?? DEFAULT_DELIVERY_ID,
    }))),
    { initialValue: { delivery: DEFAULT_DELIVERY_ID } }
  );

  readonly assessmentId = signal(DEMO_ASSESSMENT_ID);
  readonly deliveryId = computed(() => this.queryParams().delivery);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly assessment = signal<{ name: string; packageBaseHref: string; items: ItemInfo[] } | null>(null);
  readonly itemStats = signal<ItemStatistics[]>([]);

  readonly reviewItems = computed<ReviewItemEntry[]>(() => {
    const assessment = this.assessment();
    if (!assessment) return [];
    const statsById = new Map(
      this.itemStats().map((stats) => [stats.itemIdentifier ?? stats.itemId, stats]),
    );
    return assessment.items
      .map((item) => ({ item, stats: statsById.get(item.identifier) ?? null }))
      .filter(({ item, stats }) => {
        if (item.manualScoringRequired) return true;
        return stats != null && item.interactionType === 'extendedTextEntry';
      });
  });

  constructor() {
    let previousDeliveryId: string | null = null;

    effect((onCleanup) => {
      const deliveryId = this.deliveryId();
      const sortingMode = this.state.sortingMode();
      this.state.reloadGeneration();

      if (previousDeliveryId !== null && previousDeliveryId !== deliveryId) {
        this.state.resetGrading();
      }
      previousDeliveryId = deliveryId;

      this.loading.set(true);
      this.error.set(null);

      const subscription = forkJoin({
        assessment: this.api.getAssessment(DEMO_ASSESSMENT_ID),
        stats: this.api.getItemStats(deliveryId, sortingMode),
      }).subscribe({
        next: ({ assessment, stats }) => {
          this.assessment.set(assessment);
          this.itemStats.set(stats.itemStatistics);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Demo kon niet worden geladen. Start de Java backend op poort 8080.');
          this.loading.set(false);
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
