const WEBVIEW_TIMEOUT_MS = 45_000;

type ContextEntry = string | { id: string };

function contextName(entry: ContextEntry): string {
  return typeof entry === 'string' ? entry : entry.id;
}

/** Switches to the app's WebView context. Fails with the contexts it saw when the WebView does not show up in time. */
export async function switchToWebView(packageName: string): Promise<void> {
  let seen: string[] = [];
  try {
    await driver.waitUntil(
      async () => {
        seen = ((await driver.getContexts()) as ContextEntry[]).map(contextName);

        return seen.some((name) => name.startsWith('WEBVIEW_') && name.includes(packageName));
      },
      { timeout: WEBVIEW_TIMEOUT_MS, interval: 1_000 },
    );
  } catch {
    throw new Error(`No WebView context for ${packageName} within ${WEBVIEW_TIMEOUT_MS / 1000} s. Contexts seen: ${seen.join(', ') || 'none'}`);
  }
  const name = seen.find((entry) => entry.startsWith('WEBVIEW_') && entry.includes(packageName));
  await driver.switchContext(name as string);
}

export async function switchToNative(): Promise<void> {
  await driver.switchContext('NATIVE_APP');
}
