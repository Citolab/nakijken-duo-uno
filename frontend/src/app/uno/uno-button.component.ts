/**
 * UNO-compatible button fallback. Prefer native `button[uno-ng-*]` once registry access is available.
 */
import { Component, input } from '@angular/core';

@Component({
  selector: 'uno-button',
  standalone: true,
  template: `
    <button
      [attr.type]="type()"
      class="btn uno-button"
      [class.btn--primary]="variant() === 'primary'"
      [class.btn--tertiary]="variant() === 'ghost'"
      [class.uno-button--primary]="variant() === 'primary'"
      [class.uno-button--secondary]="variant() === 'secondary'"
      [class.uno-button--tertiary]="variant() === 'ghost'"
      [disabled]="disabled()"
    >
      <ng-content />
    </button>
  `,
  styles: `
    :host { display: inline-flex; }
  `,
})
export class UnoButtonComponent {
  readonly variant = input<'primary' | 'secondary' | 'ghost'>('primary');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
}
