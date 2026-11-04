import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  let requestPasswordReset: ReturnType<typeof vi.fn>;

  let spectator: Spectator<ForgotPasswordComponent>;
  const createComponent = createComponentFactory({
    component: ForgotPasswordComponent,
    providers: [
      provideRouter([]),
      {
        provide: AuthService,
        useValue: {
          requestPasswordReset: (...args: unknown[]) => requestPasswordReset(...args),
        },
      },
    ],
  });

  beforeEach(() => {
    requestPasswordReset = vi.fn().mockResolvedValue(undefined);
    spectator = createComponent();
  });

  it('asks for a username and the new password twice', () => {
    expect(spectator.query('[data-testid="reset-username"]')).toExist();
    expect(spectator.query('[data-testid="reset-password"]')).toExist();
    expect(spectator.query('[data-testid="reset-confirm"]')).toExist();
  });

  it('sends the request and shows the generic confirmation', async () => {
    spectator.typeInElement('jordy', '[data-testid="reset-username"]');
    spectator.typeInElement('a much better password', '[data-testid="reset-password"]');
    spectator.typeInElement('a much better password', '[data-testid="reset-confirm"]');
    spectator.click('[data-testid="reset-submit"]');
    await spectator.fixture.whenStable();
    spectator.detectChanges();

    expect(requestPasswordReset).toHaveBeenCalledWith('jordy', 'a much better password');
    expect(spectator.query('[data-testid="reset-submitted"]')).toExist();
    expect(spectator.query('[data-testid="reset-password"]')).toBeNull();
  });

  it('refuses a mismatched confirmation before calling the API', async () => {
    spectator.typeInElement('jordy', '[data-testid="reset-username"]');
    spectator.typeInElement('a much better password', '[data-testid="reset-password"]');
    spectator.typeInElement('a different password', '[data-testid="reset-confirm"]');
    spectator.click('[data-testid="reset-submit"]');
    await spectator.fixture.whenStable();
    spectator.detectChanges();

    expect(requestPasswordReset).not.toHaveBeenCalled();
    expect(spectator.query('[data-testid="reset-error"]')).toHaveText('The two passwords do not match.');
  });

  it('shows the backend password-policy message', async () => {
    requestPasswordReset.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: { reason: 'WEAK_PASSWORD', message: 'The password must be at least 12 characters' },
      }),
    );
    spectator.typeInElement('jordy', '[data-testid="reset-username"]');
    spectator.typeInElement('short', '[data-testid="reset-password"]');
    spectator.typeInElement('short', '[data-testid="reset-confirm"]');
    spectator.click('[data-testid="reset-submit"]');
    await spectator.fixture.whenStable();
    spectator.detectChanges();

    expect(spectator.query('[data-testid="reset-error"]')).toHaveText(
      'The password must be at least 12 characters',
    );
    expect(spectator.query('[data-testid="reset-submitted"]')).toBeNull();
  });
});
