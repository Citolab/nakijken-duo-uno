import { Component, inject, input, output, signal } from '@angular/core';
import { ReviewApiService } from '../../core/services/review-api.service';
import { ReviewStateService } from '../../core/services/review-state.service';
import { SortingMode } from '../../core/models/review.models';
import { UnoSwitchComponent } from '../../uno/uno-switch.component';

const DEMO_ASSESSMENT_ID = 'demo';

@Component({
  selector: 'app-nakijk-opties-dialog',
  standalone: true,
  imports: [UnoSwitchComponent],
  template: `
    @if (open()) {
      <div
        class="nakijk-opties-backdrop"
        role="presentation"
        (click)="close.emit()"
        (keydown.escape)="close.emit()"
      >
        <div
          class="nakijk-opties-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="nakijk-opties-title"
          (click)="$event.stopPropagation()"
        >
          <header class="nakijk-opties-dialog__header">
            <h2 id="nakijk-opties-title">Nakijk opties</h2>
            <button type="button" class="nakijk-opties-dialog__close" (click)="close.emit()" aria-label="Sluiten">
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path fill="currentColor" d="M18.3 5.71a1 1 0 0 0-1.41 0L12 10.59 7.11 5.7A1 1 0 0 0 5.7 7.11L10.59 12l-4.89 4.89a1 1 0 1 0 1.41 1.41L12 13.41l4.89 4.89a1 1 0 0 0 1.41-1.41L13.41 12l4.89-4.89a1 1 0 0 0 0-1.4z"/>
              </svg>
            </button>
          </header>

          <div class="nakijk-opties-dialog__body">
            <div class="nakijk-opties-dialog__row">
              <span>Sortering</span>
              <div class="nakijk-opties-dialog__segmented">
                <button
                  type="button"
                  [class.is-active]="state.sortingMode() === 'grading'"
                  (click)="setSortingMode('grading')"
                >
                  Score
                </button>
                <button
                  type="button"
                  [class.is-active]="state.sortingMode() === 'scan'"
                  (click)="setSortingMode('scan')"
                >
                  Gelijkheid
                </button>
              </div>
            </div>

            <div class="nakijk-opties-dialog__row">
              <span>Score markeringen tonen</span>
              <uno-switch
                [checked]="state.showHighlights()"
                (checkedChange)="state.showHighlights.set($event)"
              />
            </div>

            <div class="nakijk-opties-dialog__row">
              <span>
                Verberg bevestigde antwoorden
                @if (confirmedCount() > 0) {
                  ({{ confirmedCount() }})
                }
              </span>
              <uno-switch
                [checked]="state.hideConfirmed()"
                (checkedChange)="state.hideConfirmed.set($event)"
              />
            </div>
          </div>

          <div class="nakijk-opties-dialog__actions">
            <button
              type="button"
              class="nakijk-opties-dialog__clear"
              [disabled]="clearing()"
              (click)="clearAllScores()"
            >
              {{ clearing() ? 'Scores worden gewist…' : 'Alle scores wissen' }}
            </button>
          </div>

          <p class="nakijk-opties-dialog__hint">Deze instellingen gelden voor alle vragen.</p>
        </div>
      </div>
    }
  `,
  styles: `
    .nakijk-opties-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: flex-start;
      justify-content: center;
      padding: 5rem 1rem 1rem;
      background: rgb(15 23 42 / 0.45);
    }

    .nakijk-opties-dialog {
      width: min(100%, 24rem);
      background: #fff;
      border-radius: 0.75rem;
      box-shadow: 0 20px 40px rgb(15 23 42 / 0.18);
      color: var(--uno-color-text, #1f2937);
    }

    .nakijk-opties-dialog__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid var(--uno-color-border);
    }

    .nakijk-opties-dialog__header h2 {
      margin: 0;
      font-size: 1rem;
      font-weight: 600;
    }

    .nakijk-opties-dialog__close {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: transparent;
      color: var(--uno-color-muted);
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 0.375rem;
    }

    .nakijk-opties-dialog__close:hover {
      background: var(--uno-color-surface-muted);
      color: var(--uno-color-text, #1f2937);
    }

    .nakijk-opties-dialog__body {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      padding: 1.25rem;
      font-size: 0.875rem;
    }

    .nakijk-opties-dialog__row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }

    .nakijk-opties-dialog__segmented {
      display: inline-flex;
      border: 1px solid var(--uno-color-border);
      background: #fff;
      border-radius: 999px;
      overflow: hidden;
    }

    .nakijk-opties-dialog__segmented button {
      border: none;
      background: transparent;
      padding: 0.35rem 0.85rem;
      cursor: pointer;
      font: inherit;
      font-size: 0.8125rem;
    }

    .nakijk-opties-dialog__segmented button.is-active {
      background: var(--uno-color-primary);
      color: #fff;
    }

    .nakijk-opties-dialog__hint {
      margin: 0;
      padding: 0 1.25rem 1.25rem;
      font-size: 0.75rem;
      color: var(--uno-color-muted);
    }

    .nakijk-opties-dialog__actions {
      padding: 0 1.25rem 0.75rem;
    }

    .nakijk-opties-dialog__clear {
      width: 100%;
      border: 1px solid #d14343;
      background: #fff;
      color: #b00020;
      border-radius: 0.5rem;
      padding: 0.55rem 0.85rem;
      font: inherit;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
    }

    .nakijk-opties-dialog__clear:hover:not(:disabled) {
      background: #fff5f5;
    }

    .nakijk-opties-dialog__clear:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  `,
})
export class NakijkOptiesDialogComponent {
  readonly open = input(false);
  readonly confirmedCount = input(0);
  readonly close = output<void>();

  private readonly api = inject(ReviewApiService);
  protected readonly state = inject(ReviewStateService);
  readonly clearing = signal(false);

  setSortingMode(mode: SortingMode): void {
    this.state.setSortingMode(mode);
  }

  clearAllScores(): void {
    if (this.clearing()) {
      return;
    }
    if (!window.confirm('Weet je zeker dat je alle gegeven scores wilt wissen?')) {
      return;
    }

    this.clearing.set(true);
    this.api.clearAllScores(DEMO_ASSESSMENT_ID).subscribe({
      next: () => {
        this.state.resetGrading();
        this.state.requestReload();
        this.clearing.set(false);
        this.close.emit();
      },
      error: () => {
        this.clearing.set(false);
      },
    });
  }
}
