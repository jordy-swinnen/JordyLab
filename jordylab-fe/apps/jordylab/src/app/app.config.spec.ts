import { TestBed } from '@angular/core/testing';
import { AuthService, BiometricUnlockService } from '@jordylab-fe/shared/auth';
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
    TestBed.configureTestingModule({ providers: appConfig.providers });
  });

  it('can create the auth service', () => {
    expect(() => TestBed.inject(AuthService)).not.toThrow();
  });

  it('can create the biometric unlock service', () => {
    expect(() => TestBed.inject(BiometricUnlockService)).not.toThrow();
  });
});
