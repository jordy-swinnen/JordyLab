# shared-auth

Session authentication and account UI shared by every deployable app (`apps/jordylab` and the
`fna` / `gamecatalog` dev harnesses, spec 019). The browser keeps an HttpOnly session cookie; there
is no OIDC SDK and no token in JavaScript.

- `AuthService` — `/api/identity/me` on start-up, real-form sign-in/sign-out, password changes,
  passkey sign-in and management, and the CSRF bootstrap Angular's interceptor uses.
- `authGuard` / `roleGuard` — route protection over the signals the service exposes.
- `LoginComponent`, `RegisterComponent`, `ForgotPasswordComponent` — the three signed-out screens.
- `UserMenuComponent` — account settings and sign-out.
- `webauthn.ts` — WebAuthn JSON ↔ browser API conversion and the capability check.

This library was generated with [Nx](https://nx.dev).
