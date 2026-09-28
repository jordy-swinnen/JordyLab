import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { AwaitingApprovalComponent } from './awaiting-approval.component';

describe('AwaitingApprovalComponent', () => {
  const logout = vi.fn(() => Promise.resolve());
  const createComponent = createComponentFactory({
    component: AwaitingApprovalComponent,
    providers: [
      {
        provide: AuthService,
        useValue: { username: () => 'alex.guest@example.com', logout },
      },
    ],
  });

  let spectator: Spectator<AwaitingApprovalComponent>;

  beforeEach(() => {
    logout.mockClear();
    spectator = createComponent();
  });

  it('explains that the account is waiting for approval', () => {
    expect(spectator.element.textContent).toContain('Awaiting approval');
    expect(spectator.element.textContent).toContain(
      'administrator still has to approve',
    );
  });

  it('shows which account is signed in', () => {
    expect(spectator.element.textContent).toContain('alex.guest@example.com');
  });

  it('signs out on click', () => {
    const button = spectator.query('button') as HTMLElement;

    spectator.click(button);

    expect(logout).toHaveBeenCalled();
  });
});
