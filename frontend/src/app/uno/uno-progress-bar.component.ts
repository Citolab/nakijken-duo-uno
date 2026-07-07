import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'uno-progress-bar',
  standalone: true,
  template: `
    <progress class="uno-progress" [value]="boundedValue()" max="100">
      {{ boundedValue() }}%
    </progress>
  `,
  styles: `
    :host { display: block; }
  `,
})
export class UnoProgressBarComponent {
  readonly value = input(0);
  readonly boundedValue = computed(() => Math.min(100, Math.max(0, this.value())));
}
