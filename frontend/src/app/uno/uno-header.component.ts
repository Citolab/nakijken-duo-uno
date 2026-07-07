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
    :host { display: block; }
  `,
})
export class UnoHeaderComponent {
  readonly eyebrow = input('CheckMate demo');
  readonly title = input('Nakijken');
  readonly subtitle = input<string | undefined>(undefined);
}
