import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '@jordylab-fe/shared/auth';
import { appConfig } from './app.config';

/**
 * Boots the real application providers (router, HTTP client, theme initializer) and resolves the services the shell
 * needs at start-up. A missing provider (NG0201) otherwise only shows up as a blank page in the browser.
 */
describe('application providers', () => {
  beforeEach(() => {
    // jsdom has no matchMedia; the theme store, started by an app initializer, asks the device whether it is light or dark.
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn(() => ({ matches: false, addEventListener: vi.fn() })) as unknown as typeof window.matchMedia,
      writable: true,
      configurable: true,
    });
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('can create the auth service', () => {
    expect(() => TestBed.inject(AuthService)).not.toThrow();
  });
});
