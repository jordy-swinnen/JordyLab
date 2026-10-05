export interface AndroidE2eEnvironment {
  readonly keycloakUrl: string;
  readonly apiOrigin: string;
  readonly webUrl: string;
  readonly adminUsername: string;
  readonly adminPassword: string;
  readonly ingestClientSecret: string;
  readonly apkFirst: string;
  readonly apkNewer: string;
  readonly androidPackage: string;
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is not set. Run the Android suite through jordylab-fe/e2e/run.sh android: it builds the debug APKs, starts the throwaway stack and exports the E2E_* variables.`);
  }

  return value;
}

/** Addresses and credentials exported by e2e/run.sh. Values are used, never printed. */
export function requiredEnvironment(): AndroidE2eEnvironment {
  return {
    keycloakUrl: required('E2E_KEYCLOAK_URL'),
    apiOrigin: required('E2E_API_ORIGIN'),
    webUrl: required('E2E_BASE_URL'),
    adminUsername: required('E2E_ADMIN_USERNAME'),
    adminPassword: required('E2E_ADMIN_PASSWORD'),
    ingestClientSecret: required('E2E_INGEST_CLIENT_SECRET'),
    apkFirst: required('E2E_APK_FIRST'),
    apkNewer: required('E2E_APK_NEWER'),
    androidPackage: required('E2E_ANDROID_PACKAGE'),
  };
}
