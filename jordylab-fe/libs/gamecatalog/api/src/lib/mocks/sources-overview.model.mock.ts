import { SourcesOverview } from '../gamecatalog.models';
import { aLibraryHealthMock } from './library-health.model.mock';
import { aScanSourceMock } from './scan-source.model.mock';

export function aSourcesOverviewMock(overrides: Partial<SourcesOverview> = {}): SourcesOverview {
  return {
    sources: [aScanSourceMock()],
    health: aLibraryHealthMock(),
    ...overrides,
  };
}
