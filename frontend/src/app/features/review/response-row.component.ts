import { Component, computed, inject, input, output } from '@angular/core';
import { ReviewStateService } from '../../core/services/review-state.service';
import { UniqueResponse } from '../../core/models/review.models';

@Component({
  selector: 'app-response-row',
  standalone: true,
  imports: [],
  template: `
    <article
      class="response-row"
      [class.response-row--highlight]="highlight()"
      (click)="confirm.emit(response())"
    >
      <div class="response-row__main">
        <p class="response-row__text">{{ response().value }}</p>
        <div class="response-row__meta">
          @if (response().count > 1) {
            <span class="response-row__count">{{ response().count }}×</span>
          }
        </div>
      </div>
      <div class="response-row__scores" (click)="$event.stopPropagation()">
        @for (score of scoreOptions(); track score) {
          <button
            type="button"
            class="response-row__score"
            [class.is-selected]="effectiveScore() === score"
            (click)="scoreSelected.emit({ response: response(), score })"
          >
            {{ score }}
          </button>
        }
      </div>
    </article>
  `,
  styles: `
    .response-row {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1rem;
      background: #fff;
      border: 1px solid var(--uno-color-border);
      cursor: pointer;
    }
    .response-row:hover { border-color: var(--uno-color-primary); }
    .response-row--highlight { box-shadow: inset 3px 0 0 var(--uno-color-accent); }
    .response-row__main { flex: 1; min-width: 0; }
    .response-row__text { margin: 0; word-break: break-word; }
    .response-row__meta { display: flex; gap: 0.5rem; margin-top: 0.35rem; font-size: 0.75rem; color: var(--uno-color-muted); }
    .response-row__scores { display: flex; gap: 0.25rem; align-items: flex-start; }
    .response-row__score {
      min-width: 2rem;
      height: 2rem;
      border: 1px solid var(--uno-color-border);
      background: #fff;
      cursor: pointer;
      font: inherit;
    }
    .response-row__score.is-selected {
      background: var(--uno-color-primary);
      border-color: var(--uno-color-primary);
      color: #fff;
    }
  `,
})
export class ResponseRowComponent {
  readonly response = input.required<UniqueResponse>();
  readonly maxScore = input(1);
  readonly confirm = output<UniqueResponse>();
  readonly scoreSelected = output<{ response: UniqueResponse; score: number }>();

  private readonly state = inject(ReviewStateService);

  readonly effectiveScore = computed(() => this.state.effectiveScore(this.response()));
  readonly highlight = computed(() => this.state.showHighlights() && this.effectiveScore() != null);
  readonly scoreOptions = computed(() => Array.from({ length: (this.maxScore() ?? 0) + 1 }, (_, i) => i));
}
