/** UNO-compatible switch. Swap for `uno-ng-switch` when registry access is available. */
import { Component, input, model } from '@angular/core';

@Component({
  selector: 'uno-switch',
  standalone: true,
  template: `
    <label class="uno-switch">
      <input type="checkbox" [checked]="checked()" (change)="checked.set($any($event.target).checked)" />
      <span class="uno-switch__track" aria-hidden="true"></span>
    </label>
  `,
  styles: `
    .uno-switch { position: relative; display: inline-flex; cursor: pointer; }
    .uno-switch input { position: absolute; opacity: 0; width: 0; height: 0; }
    .uno-switch__track {
      width: 2.5rem; height: 1.25rem; background: #ccc; display: inline-block; transition: background 0.2s;
    }
    .uno-switch input:checked + .uno-switch__track { background: var(--uno-color-primary); }
    .uno-switch__track::after {
      content: ''; position: absolute; top: 2px; left: 2px; width: 1rem; height: 1rem; background: #fff; transition: transform 0.2s;
    }
    .uno-switch input:checked + .uno-switch__track::after { transform: translateX(1.25rem); }
  `,
})
export class UnoSwitchComponent {
  readonly checked = model(false);
  readonly label = input('');
}
