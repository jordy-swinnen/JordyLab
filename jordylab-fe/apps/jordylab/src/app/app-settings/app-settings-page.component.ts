import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { InstallOnPhoneCardComponent } from './install-on-phone-card.component';
import { ThemePickerComponent } from './theme-picker.component';

/**
 * Settings → App: options for this device. Open to every signed-in person on the web and on the phone: the theme choice
 * and the install entry, which replaced the native app's fingerprint section (spec 018 US4).
 */
@Component({
  selector: 'app-app-settings-page',
  standalone: true,
  imports: [PageHeaderComponent, ThemePickerComponent, InstallOnPhoneCardComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-page-header title="App" subtitle="Settings for this device." />
    <section class="mt-10 flex max-w-2xl flex-col gap-4">
      <h3 class="section-title">Appearance</h3>
      <app-theme-picker />
    </section>
    <app-install-on-phone-card />
  `,
})
export class AppSettingsPageComponent {}
