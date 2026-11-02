import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anAppUserMock } from '@jordylab-fe/settings/api';
import { DeletePersonDialogComponent } from './delete-person-dialog.component';

describe('DeletePersonDialogComponent', () => {
  // The user input is required, so it is set before the first change detection.
  const createComponent = createComponentFactory({
    component: DeletePersonDialogComponent,
    detectChanges: false,
  });

  let spectator: Spectator<DeletePersonDialogComponent>;

  beforeEach(() => {
    spectator = createComponent({
      props: { user: anAppUserMock({ id: 'u1', firstName: 'Ada', lastName: 'Palmer' }), busy: false },
    });
    spectator.detectChanges();
  });

  it('offers a delete trigger', () => {
    expect(spectator.query('[data-testid="delete-user-u1"]')).toHaveText('Delete');
  });

  it('does not delete until the confirmation is accepted', () => {
    const confirm = vi.fn();
    spectator.component.confirm.subscribe(confirm);

    expect(confirm).not.toHaveBeenCalled();
  });

  it('disables the trigger while an action is running', () => {
    spectator.setInput('busy', true);
    spectator.detectChanges();

    expect(spectator.query<HTMLButtonElement>('[data-testid="delete-user-u1"]')?.disabled).toBe(true);
  });
});
