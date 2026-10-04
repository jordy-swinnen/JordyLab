import { Component } from '@angular/core';
import { BiometricUnlockToggleComponent } from '@jordylab-fe/shared/auth';

/**
 * Settings → App: options that only make sense inside the native Android app. Reachable by admins and guests alike
 * (it holds nothing but the signed-in user's own device setting); the nav item is shown on native only.
 */
@Component({
  selector: 'app-app-settings-page',
  standalone: true,
  imports: [BiometricUnlockToggleComponent],
  template: `
    <div class="flex flex-col gap-3.5">
      <div class="eyebrow">Settings · App</div>
      <h2 class="page-title">App</h2>
      <p class="max-w-2xl text-sm text-muted-foreground">
        Options for the JordyLab Android app on this phone.
      </p>
    </div>
    <div class="mt-10 max-w-2xl">
      <lib-biometric-unlock-toggle />
    </div>
  `,
})
export class AppSettingsPageComponent {}
