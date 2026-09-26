import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let spectator: Spectator<LoginComponent>;
  let login: ReturnType<typeof vi.fn>;

  const createComponent = createComponentFactory({
    component: LoginComponent,
    providers: [{ provide: AuthService, useValue: { login: (...args: unknown[]) => login(...args) } }],
  });

  beforeEach(() => {
    login = vi.fn().mockResolvedValue(undefined);
    spectator = createComponent();
  });

  it('renders a sign-in button', () => {
    expect(spectator.query('button')).toHaveText('Sign in with Keycloak');
  });

  it('calls AuthService.login() when the button is clicked', () => {
    spectator.click('button');

    expect(login).toHaveBeenCalled();
  });
});
