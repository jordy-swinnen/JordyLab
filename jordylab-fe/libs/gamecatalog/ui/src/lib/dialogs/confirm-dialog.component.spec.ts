import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { ConfirmDialogComponent } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let spectator: Spectator<ConfirmDialogComponent>;
  const createComponent = createComponentFactory(ConfirmDialogComponent);

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
    spectator = createComponent({ props: { open: false, heading: 'Regenerate AI data?', confirmLabel: 'Regenerate' } });
  });

  it('is closed until asked to open and then shows its heading', () => {
    expect(spectator.query('dialog')?.hasAttribute('open')).toBe(false);

    spectator.setInput('open', true);

    expect(spectator.query('dialog')?.hasAttribute('open')).toBe(true);
    expect(spectator.query('h2')).toHaveText('Regenerate AI data?');
  });

  it('closes again when told to', () => {
    spectator.setInput('open', true);

    spectator.setInput('open', false);

    expect(spectator.query('dialog')?.hasAttribute('open')).toBe(false);
  });

  it('emits confirmed and cancelled from its two buttons', () => {
    const confirmed = vi.fn();
    const cancelled = vi.fn();
    spectator.output('confirmed').subscribe(confirmed);
    spectator.output('cancelled').subscribe(cancelled);

    spectator.click('[data-testid="confirm-dialog-confirm"]');
    spectator.click('[data-testid="confirm-dialog-cancel"]');

    expect(confirmed).toHaveBeenCalledTimes(1);
    expect(cancelled).toHaveBeenCalledTimes(1);
  });

  it('treats Escape as a cancel and keeps the decision with the caller', () => {
    const cancelled = vi.fn();
    spectator.output('cancelled').subscribe(cancelled);

    const event = new Event('cancel', { cancelable: true });
    spectator.query('dialog')?.dispatchEvent(event);

    expect(cancelled).toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(true);
  });

  it('labels the confirm button with the action', () => {
    expect(spectator.query('[data-testid="confirm-dialog-confirm"]')).toHaveText('Regenerate');
  });
});
