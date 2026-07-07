/**
 * UNO-compatible button. Swap for `uno-ng-button` when DUO registry access is available.
 */
import { Component, input } from '@angular/core';

@Component({
  selector: 'uno-button',
  standalone: true,
  template: `
    <button
      [attr.type]="type()"
      class="uno-button"
      [class.uno-button--secondary]="variant() === 'secondary'"
      [class.uno-button--ghost]="variant() === 'ghost'"
      [disabled]="disabled()"
    >
      <ng-content />
    </button>
  `,
  styles: `
    .uno-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      min-height: 2.5rem;
      padding: 0 1rem;
      border: 2px solid var(--uno-color-primary);
      border-radius: 0;
      background: var(--uno-color-primary);
      color: #fff;
      font: inherit;
      font-weight: 600;
      cursor: pointer;
    }
    .uno-button:hover:not(:disabled) { background: var(--uno-color-primary-hover); border-color: var(--uno-color-primary-hover); }
    .uno-button:disabled { opacity: 0.5; cursor: not-allowed; }
    .uno-button--secondary {
      background: #fff;
      color: var(--uno-color-primary);
    }
    .uno-button--ghost {
      background: transparent;
      border-color: transparent;
      color: var(--uno-color-primary);
    }
  `,
})
export class UnoButtonComponent {
  readonly variant = input<'primary' | 'secondary' | 'ghost'>('primary');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
}
