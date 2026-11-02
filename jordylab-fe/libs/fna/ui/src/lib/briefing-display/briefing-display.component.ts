import { ErrorStateComponent } from '@jordylab-fe/shared/layout';
import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { BriefingStore } from '@jordylab-fe/fna/api';
import { BriefingDisplayViewComponent } from './briefing-display-view.component';

@Component({
  selector: 'lib-briefing-display',
  standalone: true,
  imports: [BriefingDisplayViewComponent, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './briefing-display.component.html',
})
export class BriefingDisplayComponent {
  #store = inject(BriefingStore);

  briefing = this.#store.briefing;
  loading = this.#store.loading;
  error = this.#store.error;

  generate(): void {
    this.#store.generate();
  }

  reload(): void {
    this.#store.load();
  }
}
