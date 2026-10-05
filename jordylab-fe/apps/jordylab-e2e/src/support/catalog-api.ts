import type { APIRequestContext } from '@playwright/test';
import type { E2eEnvironment } from './environment';

export interface CatalogGame {
  readonly externalRef: string;
  readonly title: string;
  readonly platform: string;
}

/** The games every run puts in its throwaway catalog (through the ingest API, like a real scan would). */
export const CATALOG_GAMES: readonly CatalogGame[] = [
  { externalRef: 'snes/chrono-trigger.sfc', title: 'Chrono Trigger', platform: 'Super Nintendo' },
  { externalRef: 'snes/super-metroid.sfc', title: 'Super Metroid', platform: 'Super Nintendo' },
  { externalRef: 'n64/ocarina-of-time.z64', title: 'Ocarina of Time', platform: 'Nintendo 64' },
];

async function serviceAccountToken(request: APIRequestContext, environment: E2eEnvironment): Promise<string> {
  const response = await request.post(`${environment.keycloakUrl}/realms/jordylab/protocol/openid-connect/token`, {
    form: {
      grant_type: 'client_credentials',
      client_id: 'e2e-ingest',
      client_secret: environment.ingestClientSecret,
    },
  });
  if (!response.ok()) {
    throw new Error(`Could not get the ingest service-account token (HTTP ${response.status()})`);
  }
  const body = (await response.json()) as { access_token: string };

  return body.access_token;
}

/**
 * Fills the throwaway catalog through the app's own scan endpoint with a scanner-role token, never with SQL. The response is
 * checked: a rejected scan fails the setup with the backend's reason instead of leaving the journeys with an empty catalog.
 */
export async function ingestCatalog(request: APIRequestContext, environment: E2eEnvironment): Promise<void> {
  const token = await serviceAccountToken(request, environment);
  const response = await request.post(`${environment.apiOrigin}/api/gamecatalog/ingest/scan`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      machineId: 'e2e-machine',
      hostname: 'e2e-host',
      libraryType: 'EMUDECK',
      capturedAt: new Date().toISOString(),
      paths: [],
      games: CATALOG_GAMES,
    },
  });
  if (!response.ok()) {
    throw new Error(`Catalog ingest was rejected (HTTP ${response.status()}): ${await response.text()}`);
  }
  const result = (await response.json()) as { outcome?: string };
  if (result.outcome !== 'APPLIED') {
    throw new Error(`Catalog ingest did not apply the scan: ${JSON.stringify(result)}`);
  }
}
