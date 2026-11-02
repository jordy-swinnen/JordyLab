import { Capacitor, registerPlugin } from '@capacitor/core';
import { ThemeId } from './theme-preference.model';

interface LauncherIconPlugin {
  set(options: { theme: ThemeId }): Promise<{ theme: ThemeId }>;
}

const LauncherIcon = registerPlugin<LauncherIconPlugin>('LauncherIcon');

/**
 * Switches the Android home-screen icon to the one of the given theme (native plugin `LauncherIconPlugin`). Does nothing outside
 * the native app. The caller passes only a changed theme: switching can make a launcher drop a pinned shortcut.
 */
export async function setLauncherIcon(theme: ThemeId): Promise<void> {
  if (!Capacitor.isNativePlatform()) {
    return;
  }
  try {
    await LauncherIcon.set({ theme });
  } catch (error) {
    // An older installed build has no such plugin: the icon then simply stays as it is. Any other failure is logged.
    if ((error as { code?: string }).code !== 'UNIMPLEMENTED') {
      console.warn('Could not switch the launcher icon', error);
    }
  }
}
