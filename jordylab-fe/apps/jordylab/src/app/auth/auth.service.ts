import { Injectable, signal, Signal } from '@angular/core';
import Keycloak, { KeycloakInstance } from 'keycloak-js';
import { environment } from '../../environments/environment';

/**
 * Wraps the official `keycloak-js` SDK behind Angular signals. The host shell
 * is the only place in the frontend that knows about Keycloak; remotes see
 * just the bearer token via the {@link authInterceptor}.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  #keycloak: KeycloakInstance | null = null;
  #authenticated = signal(false);
  #username = signal<string | null>(null);
  #token = signal<string | null>(null);

  readonly isAuthenticated = this.#authenticated.asReadonly();
  readonly username: Signal<string | null> = this.#username.asReadonly();
  readonly token: Signal<string | null> = this.#token.asReadonly();

  async init(): Promise<boolean> {
    if (this.#keycloak) {
      return this.#authenticated();
    }
    const keycloak = new Keycloak({
      url: environment.keycloakUrl,
      realm: environment.keycloakRealm,
      clientId: environment.keycloakClientId,
    });
    this.#keycloak = keycloak;
    try {
      const authenticated = await keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoFallback: false,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
      this.#authenticated.set(authenticated);
      if (authenticated) {
        this.#token.set(keycloak.token ?? null);
        this.#username.set(keycloak.tokenParsed?.['preferred_username'] ?? null);
      }

      return authenticated;
    } catch (error) {
      console.error('Keycloak init failed', error);

      return false;
    }
  }

  async login(): Promise<void> {
    if (!this.#keycloak) {
      await this.init();
    }
    await this.#keycloak?.login({ redirectUri: window.location.origin });
  }

  async logout(): Promise<void> {
    await this.#keycloak?.logout({ redirectUri: window.location.origin });
  }

  async getToken(): Promise<string | null> {
    if (!this.#keycloak) {
      return null;
    }
    try {
      await this.#keycloak.updateToken(30);
    } catch (error) {
      console.error('Token refresh failed', error);
      await this.login();

      return null;
    }
    const token = this.#keycloak.token ?? null;
    this.#token.set(token);

    return token;
  }
}
