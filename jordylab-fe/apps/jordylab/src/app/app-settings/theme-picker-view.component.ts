import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ThemeDefinition, ThemePreference } from '@jordylab-fe/shared/theme/api';

/**
 * The seven choices for how the app looks: follow the device, or one of the dark and light themes. Each theme shows a small
 * swatch drawn in its own colours (an element carrying `data-theme` takes that theme's variables), so no colour is written here.
 */
@Component({
  selector: 'app-theme-picker-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="radiogroup" aria-label="Theme" class="flex flex-col gap-6">
      <div class="flex flex-col gap-2.5">
        <label [class]="optionClass(preference() === 'system')">
          <input type="radio" name="theme" class="peer sr-only" value="system" [checked]="preference() === 'system'" (change)="chosen.emit('system')" />
          <span class="flex flex-col">
            <span class="text-[15px] font-semibold">Follow device</span>
            <span class="text-[13px] text-muted-foreground">Content Led when your device is dark, Paper when it is light.</span>
          </span>
        </label>
      </div>
      @for (group of groups(); track group.heading) {
        <div class="flex flex-col gap-2.5" role="group" [attr.aria-label]="group.heading">
          <div class="label">{{ group.heading }}</div>
          <div class="grid gap-2.5 sm:grid-cols-3">
            @for (theme of group.themes; track theme.id) {
              <label [class]="optionClass(preference() === theme.id)">
                <input type="radio" name="theme" class="peer sr-only" [value]="theme.id" [checked]="preference() === theme.id" (change)="chosen.emit(theme.id)" />
                <span [attr.data-theme]="theme.id" class="relative inline-flex h-7 w-11 shrink-0 items-center justify-center rounded-md border border-input bg-background" aria-hidden="true">
                  <span class="h-3 w-3 rounded-full bg-primary"></span>
                </span>
                <span class="text-[15px] font-semibold">{{ theme.name }}</span>
              </label>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class ThemePickerViewComponent {
  themes = input.required<readonly ThemeDefinition[]>();
  preference = input.required<ThemePreference>();

  chosen = output<ThemePreference>();

  protected readonly groups = computed(() => [
    { heading: 'Dark', themes: this.themes().filter((theme) => theme.kind === 'dark') },
    { heading: 'Light', themes: this.themes().filter((theme) => theme.kind === 'light') },
  ]);

  protected optionClass(selected: boolean): string {
    const base =
      'flex min-h-11 cursor-pointer items-center gap-3 rounded-[10px] border px-3.5 py-2.5 text-foreground transition-colors focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary';

    return selected ? `${base} border-primary bg-card` : `${base} border-input hover:bg-card`;
  }
}
