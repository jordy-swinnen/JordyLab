import { Component, inject } from '@angular/core';
import { AiModelsStore } from '@jordylab-fe/settings/api';
import { AiModelsPageViewComponent } from './ai-models-page-view.component';

/** Settings → AI Models (006 US6): pick the model each AI feature uses. */
@Component({
  selector: 'lib-ai-models-page',
  standalone: true,
  imports: [AiModelsPageViewComponent],
  template: `
    <lib-ai-models-page-view
      [features]="features()"
      [loading]="loading()"
      [error]="error()"
      [pickerFeature]="pickerFeature()"
      [catalog]="catalog()"
      [catalogError]="catalogError()"
      [vendorGroups]="vendorGroups()"
      [search]="search()"
      [saving]="saving()"
      [saveError]="saveError()"
      (openPicker)="onOpenPicker($event)"
      (closePicker)="onClosePicker()"
      (searchChange)="onSearch($event)"
      (choose)="onChoose($event)"
    />
  `,
})
export class AiModelsPageComponent {
  readonly #store = inject(AiModelsStore);

  readonly features = this.#store.features;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly pickerFeature = this.#store.pickerFeature;
  readonly catalog = this.#store.catalog;
  readonly catalogError = this.#store.catalogError;
  readonly vendorGroups = this.#store.vendorGroups;
  readonly search = this.#store.search;
  readonly saving = this.#store.saving;
  readonly saveError = this.#store.saveError;

  onOpenPicker(featureKey: string): void {
    this.#store.openPicker(featureKey);
  }

  onClosePicker(): void {
    this.#store.closePicker();
  }

  onSearch(search: string): void {
    this.#store.setSearch(search);
  }

  onChoose(modelId: string): void {
    this.#store.choose(modelId);
  }
}
