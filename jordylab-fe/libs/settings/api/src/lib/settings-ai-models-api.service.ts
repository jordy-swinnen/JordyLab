import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { AiFeatureModel, ModelCatalog } from './ai-models.models';

@Injectable({ providedIn: 'root' })
export class SettingsAiModelsApiService {
  #http = inject(HttpClient);

  getFeatures(): Observable<AiFeatureModel[]> {
    return this.#http
      .get<{ features: AiFeatureModel[] }>('/api/settings/ai-models')
      .pipe(map((response) => response.features));
  }

  getCatalog(): Observable<ModelCatalog> {
    return this.#http.get<ModelCatalog>('/api/settings/ai-models/catalog');
  }

  saveModel(featureKey: string, modelId: string): Observable<void> {
    return this.#http.put<void>(`/api/settings/ai-models/${encodeURIComponent(featureKey)}`, { modelId });
  }
}
