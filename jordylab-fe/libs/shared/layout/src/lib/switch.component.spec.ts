import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { SwitchComponent } from './switch.component';

describe('SwitchComponent', () => {
  const createComponent = createComponentFactory(SwitchComponent);
  let spectator: Spectator<SwitchComponent>;

  beforeEach(() => {
    spectator = createComponent({ props: { checked: false, label: 'Unlock with fingerprint' } });
  });

  it('is a switch named by its label', () => {
    const control = spectator.query('button') as HTMLButtonElement;

    expect(control.getAttribute('role')).toBe('switch');
    expect(control.getAttribute('aria-label')).toBe('Unlock with fingerprint');
  });

  it('reports its state to assistive technology', () => {
    expect((spectator.query('button') as HTMLButtonElement).getAttribute('aria-checked')).toBe('false');

    spectator.setInput('checked', true);

    expect((spectator.query('button') as HTMLButtonElement).getAttribute('aria-checked')).toBe('true');
  });

  it('asks for the opposite state when pressed', () => {
    const checkedChange = vi.fn();
    spectator.output('checkedChange').subscribe(checkedChange);

    spectator.click('button');

    expect(checkedChange).toHaveBeenCalledWith(true);
  });
});
