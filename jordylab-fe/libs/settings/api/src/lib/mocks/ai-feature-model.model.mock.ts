import { AiFeatureModel, CatalogModel } from '../ai-models.models';

export function anAiFeatureModelMock(overrides: Partial<AiFeatureModel> = {}): AiFeatureModel {
  return {
    key: 'gamecatalog.enrichment',
    displayName: 'Game descriptions',
    moduleName: 'gamecatalog',
    description: "Writes each game's description and fills in genre and play modes.",
    currentModel: 'anthropic/claude-haiku-4.5',
    defaultModel: 'anthropic/claude-haiku-4.5',
    fallbackModel: 'claude-sonnet-5',
    modelAvailable: true,
    lastRun: {
      provider: 'openrouter',
      model: 'anthropic/claude-haiku-4.5',
      fallbackUsed: false,
      outcome: 'SUCCESS',
      failureReason: null,
      ranAt: '2026-10-01T12:00:00Z',
    },
    ...overrides,
  };
}

export function aCatalogModelMock(overrides: Partial<CatalogModel> = {}): CatalogModel {
  return {
    id: 'anthropic/claude-haiku-4.5',
    name: 'Anthropic: Claude Haiku 4.5',
    vendor: 'anthropic',
    pricingPerMillionTokens: { input: 1, output: 5 },
    contextLength: 200000,
    expiring: false,
    ...overrides,
  };
}
