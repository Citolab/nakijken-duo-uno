import { Component, computed, input } from '@angular/core';
import { UnoProgressBarComponent } from '../../uno/uno-progress-bar.component';

@Component({
  selector: 'app-review-toolbar',
  standalone: true,
  imports: [UnoProgressBarComponent],
  template: `
    <aside class="review-toolbar">
      <div class="review-toolbar__progress">
        <div class="review-toolbar__progress-label">
          <span>{{ confirmedCount() }}/{{ totalCount() }}</span>
          <span>{{ progressPercent() }}%</span>
        </div>
        <uno-progress-bar [value]="progressPercent()" />
      </div>

      @if (answerModel()) {
        <div class="review-toolbar__answer-model">
          <strong>Antwoordmodel ({{ maxScore() ?? 0 }}pt)</strong>
          <p>{{ answerModel() }}</p>
        </div>
      }

      <p class="review-toolbar__hint">Selecteer om antwoorden te bevestigen</p>
    </aside>
  `,
  styles: `
    :host {
      display: block;
      position: sticky;
      top: 0;
      z-index: 10;
    }

    .review-toolbar {
      background: var(--app-color-surface);
      border: 1px solid var(--app-color-border);
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      font-size: 0.875rem;
      max-height: 100vh;
      overflow-y: auto;
      box-shadow: var(--app-shadow-raised);
    }
    .review-toolbar__progress-label {
      display: flex;
      justify-content: space-between;
      margin-bottom: 0.35rem;
    }
    .review-toolbar__answer-model p { margin: 0.35rem 0 0; white-space: pre-wrap; }
    .review-toolbar__hint { margin: 0; color: var(--app-color-muted); font-size: 0.75rem; }
  `,
})
export class ReviewToolbarComponent {
  readonly totalCount = input(0);
  readonly confirmedCount = input(0);
  readonly answerModel = input<string | undefined>(undefined);
  readonly maxScore = input<number | undefined>(undefined);

  readonly progressPercent = computed(() => {
    const total = this.totalCount();
    if (!total) return 0;
    return Math.round((this.confirmedCount() / total) * 100);
  });
}
