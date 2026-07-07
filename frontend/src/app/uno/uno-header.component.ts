/** UNO-compatible header. Swap for `uno-ng-header` when registry access is available. */
import { Component, input } from '@angular/core';

@Component({
  selector: 'uno-header',
  standalone: true,
  template: `
    <header class="uno-header">
      <div class="uno-header__inner">
        <div>
          <p class="uno-header__eyebrow">{{ eyebrow() }}</p>
          <h1 class="uno-header__title">{{ title() }}</h1>
          @if (subtitle()) {
            <p class="uno-header__subtitle">{{ subtitle() }}</p>
          }
        </div>
        <ng-content select="[header-actions]" />
      </div>
    </header>
  `,
  styles: `
    .uno-header {
      background: var(--uno-color-primary);
      color: #fff;
      padding: 1.25rem 1.5rem;
    }
    .uno-header__inner {
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }
    .uno-header__eyebrow {
      margin: 0 0 0.25rem;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      opacity: 0.85;
    }
    .uno-header__title { margin: 0; font-size: 1.5rem; font-weight: 700; }
    .uno-header__subtitle { margin: 0.25rem 0 0; opacity: 0.9; }
  `,
})
export class UnoHeaderComponent {
  readonly eyebrow = input('CheckMate demo');
  readonly title = input('Nakijken');
  readonly subtitle = input<string | undefined>(undefined);
}
