# shared-auth

Keycloak authentication shared by every deployable app (`apps/jordylab`, `apps/fna`,
`apps/gamecatalog`). Originally lived only in `apps/jordylab/src/app/auth/`; extracted here so the
standalone dev harnesses (`apps/fna`, `apps/gamecatalog`) can also authenticate against the real
`jordylab` Keycloak realm instead of calling the backend with no bearer token at all.

This library was generated with [Nx](https://nx.dev).
