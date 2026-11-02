import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aUserSessionMock } from '@jordylab-fe/settings/api';
import { PersonSessionsComponent } from './person-sessions.component';

describe('PersonSessionsComponent', () => {
  const createComponent = createComponentFactory({
    component: PersonSessionsComponent,
    detectChanges: false,
  });

  let spectator: Spectator<PersonSessionsComponent>;

  const render = (sessions = [aUserSessionMock()]): Spectator<PersonSessionsComponent> => {
    const created = createComponent({
      props: { sessions, loading: false, actioning: false, error: null },
    });
    created.detectChanges();

    return created;
  };

  it('lists a web session and a scanner offline session with their kind', () => {
    spectator = render([
      aUserSessionMock({ id: 'web-1', kind: 'WEB' }),
      aUserSessionMock({ id: 'scanner-1', kind: 'SCANNER', offline: true }),
    ]);

    expect(spectator.element).toHaveText('Web');
    expect(spectator.element).toHaveText('Scanner');
    expect(spectator.element).toHaveText('(offline session)');
  });

  it('ends only the session that was chosen', () => {
    const endOne = vi.fn();
    spectator = render([aUserSessionMock({ id: 'web-1' }), aUserSessionMock({ id: 'web-2' })]);
    spectator.component.endOne.subscribe(endOne);

    spectator.click('[data-testid="end-session-web-2"]');

    expect(endOne).toHaveBeenCalledWith(expect.objectContaining({ id: 'web-2' }));
  });

  it('asks before ending every session', () => {
    const endAll = vi.fn();
    spectator = render();
    spectator.component.endAll.subscribe(endAll);

    spectator.click('[data-testid="end-all-sessions"]');
    expect(endAll).not.toHaveBeenCalled();
    expect(spectator.query('[data-testid="confirm-end-all"]')).toBeTruthy();

    spectator.click('[data-testid="confirm-end-all"]');
    expect(endAll).toHaveBeenCalled();
  });

  it('can cancel ending every session', () => {
    const endAll = vi.fn();
    spectator = render();
    spectator.component.endAll.subscribe(endAll);

    spectator.click('[data-testid="end-all-sessions"]');
    spectator.click('[data-testid="cancel-end-all"]');

    expect(endAll).not.toHaveBeenCalled();
    expect(spectator.query('[data-testid="end-all-sessions"]')).toBeTruthy();
  });

  it('says when there are no sessions', () => {
    spectator = render([]);

    expect(spectator.element).toHaveText('No active sessions.');
  });

  it('disables every control while an action is running', () => {
    spectator = render();
    spectator.setInput('actioning', true);
    spectator.detectChanges();

    expect(spectator.query<HTMLButtonElement>('[data-testid="end-all-sessions"]')?.disabled).toBe(true);
  });
});
