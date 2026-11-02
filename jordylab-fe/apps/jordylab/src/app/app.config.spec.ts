import { TestBed } from '@angular/core/testing';
import { AuthService } from '@jordylab-fe/shared/auth';
import { appConfig } from './app.config';
import { createPreBootstrapAuth } from './pre-bootstrap-auth';
import { environment } from '../environments/environment';

/**
 * Boots the real application providers (router, HTTP interceptors, auth config) and resolves the services the shell
 * needs at start-up. A missing provider (NG0201) otherwise only shows up as a blank page on a device.
 */
describe('application providers', () => {
  it('can create the auth service in the throwaway injector main.ts uses before bootstrapping', () => {
    expect(() => createPreBootstrapAuth(environment)).not.toThrow();
  });

  beforeEach(() => {
    // jsdom has no matchMedia; the theme store, started by an app initializer, asks the device whether it is light or dark.
    window.matchMedia = vi.fn(() => ({ matches: false, addEventListener: vi.fn() })) as unknown as typeof window.matchMedia;
    TestBed.configureTestingModule({ providers: appConfig.providers });
  });

  it('can create the auth service', () => {
    expect(() => TestBed.inject(AuthService)).not.toThrow();
  });
});
