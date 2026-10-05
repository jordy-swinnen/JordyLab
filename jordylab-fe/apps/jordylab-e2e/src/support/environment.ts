export interface E2eEnvironment {
  readonly baseUrl: string;
  readonly keycloakUrl: string;
  readonly apiOrigin: string;
  readonly adminUsername: string;
  readonly adminPassword: string;
  readonly ingestClientSecret: string;
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `${name} is not set. The web suite needs its throwaway environment: run it with jordylab-fe/e2e/run.sh web (it exports E2E_BASE_URL, E2E_KEYCLOAK_URL, E2E_API_ORIGIN and the per-run credentials).`,
    );
  }

  return value;
}

/** The per-run address and credentials exported by e2e/run.sh. Values are used, never printed. */
export function requiredEnvironment(): E2eEnvironment {
  return {
    baseUrl: required('E2E_BASE_URL'),
    keycloakUrl: required('E2E_KEYCLOAK_URL'),
    apiOrigin: required('E2E_API_ORIGIN'),
    adminUsername: required('E2E_ADMIN_USERNAME'),
    adminPassword: required('E2E_ADMIN_PASSWORD'),
    ingestClientSecret: required('E2E_INGEST_CLIENT_SECRET'),
  };
}
