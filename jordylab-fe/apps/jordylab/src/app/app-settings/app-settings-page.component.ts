import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { BiometricUnlockToggleComponent } from '@jordylab-fe/shared/auth';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { PlatformService } from '@jordylab-fe/shared/platform/api';
import { ThemePickerComponent } from './theme-picker.component';

/**
 * Settings → App: options for this device. Open to every signed-in person on the web and in the Android app: the theme choice is
 * for everyone, while fingerprint unlock only exists inside the Android app.
 */
@Component({
  selector: 'app-app-settings-page',
  standalone: true,
  imports: [BiometricUnlockToggleComponent, PageHeaderComponent, ThemePickerComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-page-header title="App" subtitle="Settings for this device." />
    <section class="mt-10 flex max-w-2xl flex-col gap-4">
      <h3 class="section-title">Appearance</h3>
      <app-theme-picker />
    </section>
    @if (platform.isNative()) {
      <section class="mt-10 flex max-w-2xl flex-col gap-4">
        <h3 class="section-title">Sign-in</h3>
        <lib-biometric-unlock-toggle />
      </section>
    }
  `,
})
export class AppSettingsPageComponent {
  protected readonly platform = inject(PlatformService);
}
