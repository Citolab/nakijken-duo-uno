import { Component, input } from '@angular/core';

@Component({
  selector: 'uno-progress-bar',
  standalone: true,
  template: `
    <div class="uno-progress" role="progressbar" [attr.aria-valuenow]="value()" aria-valuemin="0" aria-valuemax="100">
      <div class="uno-progress__fill" [style.width.%]="value()"></div>
    </div>
  `,
  styles: `
    .uno-progress {
      height: 0.5rem;
      background: #fff;
      border: 1px solid var(--uno-color-border);
      overflow: hidden;
    }
    .uno-progress__fill {
      height: 100%;
      background: var(--uno-color-accent);
      transition: width 0.25s ease;
    }
  `,
})
export class UnoProgressBarComponent {
  readonly value = input(0);
}
