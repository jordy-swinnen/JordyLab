import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { GameDescription } from '@jordylab-fe/gamecatalog/api';
import { descriptionHeading, displayModel, factSourcesLine } from './description-heading';

const aDescription = (overrides: Partial<GameDescription> = {}): GameDescription => ({
  text: 'Some text.',
  source: 'AI',
  model: null,
  requestedModel: null,
  writtenAt: null,
  ...overrides,
});

describe('descriptionHeading', () => {
  it('names the model that answered, with the provider prefix dropped and the full id in the tooltip', () => {
    expect(descriptionHeading(aDescription({ model: 'some-vendor/model-x-1.2' }))).toEqual({
      text: 'written by model-x-1.2',
      tooltip: 'some-vendor/model-x-1.2',
    });
  });

  it('names the router and the model it picked', () => {
    expect(descriptionHeading(aDescription({ model: 'vendor/model-x', requestedModel: 'router-1' }))).toEqual({
      text: 'written by model-x · picked by router-1',
      tooltip: 'vendor/model-x · router-1',
    });
  });

  it('says the model was not reported when only the selected id is known', () => {
    expect(descriptionHeading(aDescription({ requestedModel: 'router-1' }))).toEqual({
      text: 'written by router-1 (model not reported)',
      tooltip: 'router-1',
    });
  });

  it('says only "written by AI" for text from before the model was recorded', () => {
    expect(descriptionHeading(aDescription())).toEqual({ text: 'written by AI', tooltip: null });
  });

  it('credits Steam for a Steam description and never mentions a model', () => {
    expect(descriptionHeading(aDescription({ source: 'STEAM', model: 'vendor/model-x' }))).toEqual({
      text: 'description from Steam',
      tooltip: null,
    });
  });

  it('shows a model id without a provider as it is', () => {
    expect(displayModel('router-1')).toBe('router-1');
  });
});

describe('factSourcesLine', () => {
  it('lists both sources in the Spec sheet style', () => {
    expect(factSourcesLine({ facts: 'STEAM', multiplayer: 'IGDB', playerCount: null })).toBe('Source · facts from Steam · multiplayer from IGDB');
  });

  it('lists what is known and nothing for the rest', () => {
    expect(factSourcesLine({ facts: 'AI', multiplayer: null, playerCount: null })).toBe('Source · facts from AI');
    expect(factSourcesLine({ facts: null, multiplayer: 'STEAM', playerCount: null })).toBe('Source · multiplayer from Steam');
    expect(factSourcesLine({ facts: null, multiplayer: null, playerCount: null })).toBeNull();
  });

  it('names the player count source apart from the multiplayer source', () => {
    expect(factSourcesLine({ facts: null, multiplayer: 'IGDB', playerCount: 'THEGAMESDB' })).toBe(
      'Source · multiplayer from IGDB · player count from TheGamesDB',
    );
    expect(factSourcesLine({ facts: null, multiplayer: null, playerCount: 'IGDB' })).toBe('Source · player count from IGDB');
  });
});

describe('the game page code', () => {
  it('contains no vendor or model name: authorship comes from the server', () => {
    const directory = __dirname;
    const sources = ['game-detail-view.component.html', 'game-detail-view.component.ts', 'description-heading.ts']
      .map((file) => readFileSync(join(directory, file), 'utf8'))
      .join('\n');

    expect(sources).not.toMatch(/claude|anthropic|openai|gpt|haiku|sonnet|opus|gemini|llama/i);
  });
});
