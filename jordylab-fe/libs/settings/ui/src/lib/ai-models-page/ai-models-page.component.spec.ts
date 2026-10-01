import { computed, signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aCatalogModelMock,
  AiFeatureModel,
  AiModelsStore,
  anAiFeatureModelMock,
  ModelCatalog,
  VendorGroup,
} from '@jordylab-fe/settings/api';
import { AiModelsPageComponent } from './ai-models-page.component';

describe('AiModelsPageComponent', () => {
  const features = signal<AiFeatureModel[]>([]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pickerFeatureKey = signal<string | null>(null);
  const catalog = signal<ModelCatalog | null>(null);
  const catalogError = signal<string | null>(null);
  const vendorGroups = signal<VendorGroup[]>([]);
  const search = signal('');
  const saving = signal(false);
  const saveError = signal<string | null>(null);
  const openPicker = vi.fn<AiModelsStore['openPicker']>();
  const closePicker = vi.fn<AiModelsStore['closePicker']>();
  const setSearch = vi.fn<AiModelsStore['setSearch']>();
  const choose = vi.fn<AiModelsStore['choose']>();

  const storeMock = {
    features: features.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    pickerFeature: computed(() => features().find((feature) => feature.key === pickerFeatureKey()) ?? null),
    catalog: catalog.asReadonly(),
    catalogError: catalogError.asReadonly(),
    vendorGroups: vendorGroups.asReadonly(),
    search: search.asReadonly(),
    saving: saving.asReadonly(),
    saveError: saveError.asReadonly(),
    openPicker,
    closePicker,
    setSearch,
    choose,
  };

  let spectator: Spectator<AiModelsPageComponent>;
  const createComponent = createComponentFactory({
    component: AiModelsPageComponent,
    providers: [{ provide: AiModelsStore, useValue: storeMock }],
  });

  const haiku = aCatalogModelMock();
  const router = aCatalogModelMock({
    id: 'openrouter/auto',
    name: 'Auto Router',
    vendor: 'openrouter',
    pricingPerMillionTokens: { input: null, output: null },
    expiring: true,
  });

  beforeEach(() => {
    vi.clearAllMocks();
    features.set([anAiFeatureModelMock()]);
    pickerFeatureKey.set(null);
    catalog.set(null);
    catalogError.set(null);
    vendorGroups.set([]);
    saveError.set(null);
    spectator = createComponent();
  });

  it('shows each feature with its model, fallback and last run', () => {
    const row = spectator.query('[data-testid="ai-feature"]');

    expect(row).toHaveText('Game descriptions');
    expect(spectator.query('[data-testid="current-model"]')).toHaveText('anthropic/claude-haiku-4.5');
    expect(row).toHaveText('Fallback: claude-sonnet-5');
    expect(spectator.query('[data-testid="last-run"]')).toHaveText('Last run succeeded via openrouter');
    expect(spectator.query('[data-testid="model-unavailable"]')).toBeNull();
  });

  it('flags a saved model the gateway no longer offers and a failed fallback run', () => {
    features.set([
      anAiFeatureModelMock({
        currentModel: 'gone/model',
        modelAvailable: false,
        lastRun: {
          provider: 'anthropic',
          model: 'claude-sonnet-5',
          fallbackUsed: true,
          outcome: 'FAILURE',
          failureReason: 'TIMEOUT',
          ranAt: '2026-10-01T12:00:00Z',
        },
      }),
    ]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="model-unavailable"]')).toExist();
    expect(spectator.query('[data-testid="last-run"]')).toHaveText('Last run failed via fallback (anthropic) · TIMEOUT');
    expect(spectator.query('[data-testid="ai-feature"]')).toHaveText('default anthropic/claude-haiku-4.5');
  });

  it('opens the picker for a feature', () => {
    spectator.click('[data-testid="change-model"]');

    expect(openPicker).toHaveBeenCalledWith('gamecatalog.enrichment');
  });

  it('lists the catalog by vendor with prices, and saves the chosen model', () => {
    pickerFeatureKey.set('gamecatalog.enrichment');
    catalog.set({ fetchedAt: '2026-10-01T12:00:00Z', fresh: true, models: [haiku, router] });
    vendorGroups.set([
      { vendor: 'anthropic', models: [haiku] },
      { vendor: 'openrouter', models: [router] },
    ]);
    spectator.detectChanges();

    const models = spectator.queryAll('[data-testid="catalog-model"]');
    expect(models).toHaveLength(2);
    expect(models[0]).toHaveText('$1 in · $5 out / 1M tokens');
    expect(models[1]).toHaveText('router-priced');
    expect(models[1]).toHaveText('expiring');
    expect(spectator.query('[data-testid="stale-catalog"]')).toBeNull();

    spectator.click(models[1]);
    spectator.typeInElement('sonnet', '#model-search');

    expect(choose).toHaveBeenCalledWith('openrouter/auto');
    expect(setSearch).toHaveBeenCalledWith('sonnet');
  });

  it('says when the list is stale and shows why a save was refused', () => {
    pickerFeatureKey.set('gamecatalog.enrichment');
    catalog.set({ fetchedAt: '2026-10-01T12:00:00Z', fresh: false, models: [haiku] });
    saveError.set('That model is no longer offered by the gateway. Pick another one.');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="stale-catalog"]')).toExist();
    expect(spectator.query('[data-testid="save-error"]')).toHaveText('no longer offered');

    spectator.click('[data-testid="close-picker"]');

    expect(closePicker).toHaveBeenCalled();
  });
});
