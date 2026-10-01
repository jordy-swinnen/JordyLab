/** GET /api/settings/ai-models (006 contracts/settings-ai-models-api.md). */
export interface AiFeatureModel {
  key: string;
  displayName: string;
  moduleName: string;
  description: string;
  currentModel: string;
  defaultModel: string;
  fallbackModel: string;
  /** False when the saved model has disappeared from the gateway — calls then use the fallback. */
  modelAvailable: boolean;
  lastRun: AiLastRun | null;
}

export interface AiLastRun {
  provider: string;
  model: string;
  fallbackUsed: boolean;
  outcome: 'SUCCESS' | 'FAILURE';
  failureReason: string | null;
  ranAt: string;
}

/** GET /api/settings/ai-models/catalog. */
export interface ModelCatalog {
  fetchedAt: string;
  /** False when the gateway is unreachable and a cached list is shown. */
  fresh: boolean;
  models: CatalogModel[];
}

export interface CatalogModel {
  id: string;
  name: string;
  vendor: string;
  /** USD per million tokens; null for router-priced models. */
  pricingPerMillionTokens: { input: number | null; output: number | null };
  contextLength: number | null;
  expiring: boolean;
}
