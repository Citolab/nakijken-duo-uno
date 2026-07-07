/** UNO-compatible switch. Swap for `uno-ng-switch` when registry access is available. */
import { Component, input, model } from '@angular/core';

@Component({
  selector: 'uno-switch',
  standalone: true,
  template: `
    <label class="uno-switch">
      @if (label()) {
        <span class="screenreader-only">{{ label() }}</span>
      }
      <input type="checkbox" [checked]="checked()" (change)="checked.set($any($event.target).checked)" />
      <span class="uno-switch__track" aria-hidden="true"></span>
    </label>
  `,
  styles: `
    :host { display: inline-flex; }
  `,
})
export class UnoSwitchComponent {
  readonly checked = model(false);
  readonly label = input('');
}
