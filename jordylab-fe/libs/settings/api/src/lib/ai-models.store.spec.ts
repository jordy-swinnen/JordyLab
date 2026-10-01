import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, throwError } from 'rxjs';
import { AiModelsStore } from './ai-models.store';
import { aCatalogModelMock, anAiFeatureModelMock } from './mocks/ai-feature-model.model.mock';
import { SettingsAiModelsApiService } from './settings-ai-models-api.service';

describe('AiModelsStore', () => {
  let spectator: SpectatorService<AiModelsStore>;
  const getFeatures = vi.fn<SettingsAiModelsApiService['getFeatures']>();
  const getCatalog = vi.fn<SettingsAiModelsApiService['getCatalog']>();
  const saveModel = vi.fn<SettingsAiModelsApiService['saveModel']>();

  const createService = createServiceFactory({
    service: AiModelsStore,
    providers: [{ provide: SettingsAiModelsApiService, useValue: { getFeatures, getCatalog, saveModel } }],
  });

  const enrichment = anAiFeatureModelMock();
  const catalog = {
    fetchedAt: '2026-10-01T12:00:00Z',
    fresh: true,
    models: [
      aCatalogModelMock({ id: 'openai/gpt-6-luna-pro', name: 'OpenAI: Luna Pro', vendor: 'openai' }),
      aCatalogModelMock(),
      aCatalogModelMock({ id: 'anthropic/claude-sonnet-5', name: 'Anthropic: Claude Sonnet 5' }),
    ],
  };

  beforeEach(() => {
    getFeatures.mockReset().mockReturnValue(of([enrichment]));
    getCatalog.mockReset().mockReturnValue(of(catalog));
    saveModel.mockReset();
    spectator = createService();
  });

  it('loads the features on creation', () => {
    expect(spectator.service.features()).toEqual([enrichment]);
    expect(spectator.service.loading()).toBe(false);
  });

  it('reports a load failure', () => {
    getFeatures.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.load();

    expect(spectator.service.error()).toBe('Failed to load the AI features.');
    expect(spectator.service.loading()).toBe(false);
  });

  it('loads the catalog once, when the picker first opens', () => {
    expect(getCatalog).not.toHaveBeenCalled();

    spectator.service.openPicker('gamecatalog.enrichment');
    spectator.service.closePicker();
    spectator.service.openPicker('gamecatalog.enrichment');

    expect(getCatalog).toHaveBeenCalledTimes(1);
    expect(spectator.service.pickerFeature()).toEqual(enrichment);
  });

  it('groups the matching models by vendor', () => {
    spectator.service.openPicker('gamecatalog.enrichment');

    expect(spectator.service.vendorGroups().map((group) => group.vendor)).toEqual(['anthropic', 'openai']);

    spectator.service.setSearch('SONNET');

    expect(spectator.service.vendorGroups()).toEqual([
      { vendor: 'anthropic', models: [catalog.models[2]] },
    ]);
  });

  it('says when the catalog cannot be loaded', () => {
    getCatalog.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    spectator.service.openPicker('gamecatalog.enrichment');

    expect(spectator.service.catalogError()).toBe('The gateway model list is unavailable right now.');
  });

  it('saves the chosen model, closes the picker and reloads', () => {
    saveModel.mockReturnValue(of(undefined));
    spectator.service.openPicker('gamecatalog.enrichment');

    spectator.service.choose('openai/gpt-6-luna-pro');

    expect(saveModel).toHaveBeenCalledWith('gamecatalog.enrichment', 'openai/gpt-6-luna-pro');
    expect(spectator.service.pickerFeatureKey()).toBeNull();
    expect(getFeatures).toHaveBeenCalledTimes(2);
  });

  it('keeps the picker open with a readable reason when saving is refused', () => {
    saveModel.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'MODEL_UNAVAILABLE' } })),
    );
    spectator.service.openPicker('gamecatalog.enrichment');

    spectator.service.choose('gone/model');

    expect(spectator.service.saveError()).toBe('That model is no longer offered by the gateway. Pick another one.');
    expect(spectator.service.pickerFeatureKey()).toBe('gamecatalog.enrichment');
    expect(spectator.service.saving()).toBe(false);
  });
});
