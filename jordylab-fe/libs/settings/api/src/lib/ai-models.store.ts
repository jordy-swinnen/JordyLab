import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { AiFeatureModel, CatalogModel, ModelCatalog } from './ai-models.models';
import { SettingsAiModelsApiService } from './settings-ai-models-api.service';

const SAVE_ERRORS: Record<string, string> = {
  MODEL_UNAVAILABLE: 'That model is no longer offered by the gateway. Pick another one.',
  BLANK_MODEL: 'Pick a model first.',
  UNKNOWN_FEATURE: 'This AI feature no longer exists. Reload the page.',
};

export interface VendorGroup {
  vendor: string;
  models: CatalogModel[];
}

/**
 * AI Models settings (006 US6): one model per AI feature, picked from the gateway's catalog. A saved model applies
 * from the next AI call; the catalog is loaded when the picker first opens.
 */
@Injectable({ providedIn: 'root' })
export class AiModelsStore {
  #api = inject(SettingsAiModelsApiService);

  readonly #features = signal<AiFeatureModel[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #catalog = signal<ModelCatalog | null>(null);
  readonly #catalogError = signal<string | null>(null);
  readonly #pickerFeatureKey = signal<string | null>(null);
  readonly #search = signal('');
  readonly #saving = signal(false);
  readonly #saveError = signal<string | null>(null);

  readonly features = this.#features.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly catalog = this.#catalog.asReadonly();
  readonly catalogError = this.#catalogError.asReadonly();
  readonly pickerFeatureKey = this.#pickerFeatureKey.asReadonly();
  readonly search = this.#search.asReadonly();
  readonly saving = this.#saving.asReadonly();
  readonly saveError = this.#saveError.asReadonly();

  readonly pickerFeature = computed(
    () => this.#features().find((feature) => feature.key === this.#pickerFeatureKey()) ?? null,
  );

  /** Catalog models matching the search (id or name), grouped by vendor in alphabetical order. */
  readonly vendorGroups = computed<VendorGroup[]>(() => {
    const needle = this.#search().trim().toLowerCase();
    const models = (this.#catalog()?.models ?? []).filter(
      (model) => !needle || model.id.toLowerCase().includes(needle) || model.name.toLowerCase().includes(needle),
    );
    const groups = new Map<string, CatalogModel[]>();
    for (const model of models) {
      groups.set(model.vendor, [...(groups.get(model.vendor) ?? []), model]);
    }

    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([vendor, vendorModels]) => ({ vendor, models: vendorModels }));
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);
    this.#api
      .getFeatures()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load the AI features.');

          return of<AiFeatureModel[] | null>(null);
        }),
      )
      .subscribe((features) => {
        if (features) {
          this.#features.set(features);
        }
        this.#loading.set(false);
      });
  }

  openPicker(featureKey: string): void {
    this.#pickerFeatureKey.set(featureKey);
    this.#search.set('');
    this.#saveError.set(null);
    if (!this.#catalog()) {
      this.#loadCatalog();
    }
  }

  closePicker(): void {
    this.#pickerFeatureKey.set(null);
    this.#saveError.set(null);
  }

  setSearch(search: string): void {
    this.#search.set(search);
  }

  choose(modelId: string): void {
    const featureKey = this.#pickerFeatureKey();
    if (!featureKey || this.#saving()) {
      return;
    }
    this.#saving.set(true);
    this.#saveError.set(null);
    this.#api
      .saveModel(featureKey, modelId)
      .pipe(
        catchError((error: HttpErrorResponse) => {
          this.#saveError.set(SAVE_ERRORS[error.error?.reason] ?? 'Saving the model failed. Please try again.');

          return of(false);
        }),
      )
      .subscribe((result) => {
        this.#saving.set(false);
        if (result !== false) {
          this.#pickerFeatureKey.set(null);
          this.load();
        }
      });
  }

  #loadCatalog(): void {
    this.#catalogError.set(null);
    this.#api
      .getCatalog()
      .pipe(
        catchError(() => {
          this.#catalogError.set('The gateway model list is unavailable right now.');

          return of(null);
        }),
      )
      .subscribe((catalog) => {
        if (catalog) {
          this.#catalog.set(catalog);
        }
      });
  }
}
