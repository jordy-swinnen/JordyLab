import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { THEME_DEFINITIONS, ThemePreference, ThemeStore } from '@jordylab-fe/shared/theme/api';
import { ThemePickerComponent } from './theme-picker.component';

describe('ThemePickerComponent', () => {
  const preference = signal<ThemePreference>('system');
  const choose = vi.fn();
  const createComponent = createComponentFactory({
    component: ThemePickerComponent,
    providers: [{ provide: ThemeStore, useValue: { themes: THEME_DEFINITIONS, preference: preference.asReadonly(), choose } }],
  });
  let spectator: Spectator<ThemePickerComponent>;

  beforeEach(() => {
    preference.set('system');
    choose.mockReset();
    spectator = createComponent();
  });

  it('offers Follow device and the six themes as one radio group', () => {
    const values = spectator.queryAll<HTMLInputElement>('input[type="radio"]').map((radio) => radio.value);

    expect(values).toEqual(['system', 'content', 'oxblood', 'flare', 'paper', 'glasshouse', 'heather']);
    expect(spectator.query('[role="radiogroup"]')).not.toBeNull();
  });

  it('checks the option that matches the stored preference', () => {
    preference.set('paper');
    spectator.detectChanges();

    const checked = spectator.queryAll<HTMLInputElement>('input[type="radio"]').filter((radio) => radio.checked);

    expect(checked.map((radio) => radio.value)).toEqual(['paper']);
  });

  it('asks the store to choose the theme the person picks', () => {
    const heather = spectator.queryAll<HTMLInputElement>('input[type="radio"]').find((radio) => radio.value === 'heather');

    spectator.dispatchFakeEvent(heather as HTMLInputElement, 'change');

    expect(choose).toHaveBeenCalledWith('heather');
  });

  it('asks the store to follow the device when that is picked', () => {
    preference.set('oxblood');
    spectator.detectChanges();
    const system = spectator.queryAll<HTMLInputElement>('input[type="radio"]').find((radio) => radio.value === 'system');

    spectator.dispatchFakeEvent(system as HTMLInputElement, 'change');

    expect(choose).toHaveBeenCalledWith('system');
  });
});
