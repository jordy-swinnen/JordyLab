---
name: angular-signal-store
description: Generate an NgRx-free Angular signal-based state management service (a "signal store") for a domain — private writable signals, public readonly signals, computed selectors, and methods that call the API and mutate state directly. Use whenever the user asks to add state management, a "store," or a service to manage API data for an Angular domain or feature module; wants to replace NgRx with signals; mentions "signal store," managing loading/error/derived state for API calls in Angular; or is scaffolding a new Nx api library (e.g. libs/domain-name/api) that needs to expose reactive state to UI components. Applies to Angular 17+ apps already using signals, inject(), and zoneless change detection.
---

# Angular Signal Store

A signal store is a plain `@Injectable` service that owns a domain's state as
private writable signals and exposes it as public readonly signals plus
computed selectors. No actions, reducers, effects, or `@ngrx/signals` — the
store's own methods mutate state directly once an API call resolves. This
skill generates one store per domain, following the conventions below.

## Before generating: pick a variant

Two variants exist. Default to Variant A unless the domain is clearly
read-only — most real domains end up needing at least one mutation.

| Signal | Use |
|---|---|
| Any create/update/delete, optimistic updates, or combining multiple data sources | **Variant A — manual signals** |
| A single GET endpoint, read + refresh only, nothing written back from the UI | **Variant B — `httpResource()`** |

If it isn't obvious from the request, ask one question: "Does this data
ever get created or edited from the UI, or is it read-only?"

**Verify before using Variant B.** `httpResource()`, `resource()`, and
`rxResource()` have shifted experimental/stable status across recent
Angular releases — check the installed `@angular/core` version's changelog
(or `ng version`) before committing to Variant B in anything beyond a
prototype. When unsure, default to Variant A — it has no dependency on
Resource API stability at all.

## Variant A — manual signals

Replace `Domain` / `domain` / `Entity` throughout with the real names:

```typescript
// libs/<domain>/api/src/lib/<domain>.store.ts
import { inject, Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, tap } from 'rxjs';
import { Entity } from './<domain>.model';

@Injectable({ providedIn: 'root' })
export class DomainStore {
  readonly #http = inject(HttpClient);
  readonly #apiUrl = '/api/<domain>';

  readonly #items = signal<Entity[]>([]);
  readonly #loading = signal(false);
  readonly #error = signal<string | null>(null);

  readonly items = this.#items.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();

  readonly hasItems = computed(() => this.#items().length > 0);

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#http.get<Entity[]>(this.#apiUrl).pipe(
      tap((items) => this.#items.set(items)),
      catchError(() => {
        this.#error.set('Failed to load <domain>');

        return of([]);
      }),
    ).subscribe(() => this.#loading.set(false));
  }

  add(item: Entity): void {
    this.#items.update((current) => [item, ...current]);
  }

  remove(id: string): void {
    this.#items.update((current) => current.filter((entry) => entry.id !== id));
  }
}
```

This is a starting skeleton, not a rigid contract — adapt method names,
endpoint shape, and add whatever `computed()` selectors save the UI from
recomputing the same derived value inline in multiple templates.

## Variant B — `httpResource()`

```typescript
// libs/<domain>/api/src/lib/<domain>.store.ts
import { inject, Injectable, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Entity } from './<domain>.model';

@Injectable({ providedIn: 'root' })
export class DomainStore {
  readonly #resource = httpResource<Entity[]>(() => '/api/<domain>', {
    defaultValue: [],
  });

  readonly items = this.#resource.value;
  readonly loading = this.#resource.isLoading;
  readonly error = this.#resource.error;

  readonly hasItems = computed(() => this.items().length > 0);

  refresh(): void {
    this.#resource.reload();
  }
}
```

## Conventions to follow

- `inject()`, never constructor injection.
- `#field` (JS private), never TypeScript `private`.
- Writable signals are always private (`#`); only expose `.asReadonly()` or
  a `computed()`. Nothing outside the store should call `.set()` or
  `.update()` on store state directly — mutation happens only through named
  methods on the store itself.
- Name the class `<Domain>Store` (e.g. `BriefingStore`, `GameCatalogStore`).
- Explicit types on every public method signature — no implicit `any`.

## File placement (Nx)

```
libs/<domain>/api/src/lib/
├── <domain>.store.ts
├── <domain>.model.ts
└── index.ts          # barrel: export * from './lib/<domain>.store'
```

- The store belongs in a `type:api` lib, never `type:ui`.
- `type:ui` components `inject()` the store directly — the store *is* the
  facade, no separate facade layer needed on top.
- Respect existing Nx `depConstraints`: a store in `scope:X` shouldn't be
  imported from `scope:Y` unless that state genuinely belongs in
  `scope:shared`. A cross-domain need is a signal the state should move to
  a shared store, not a reason to relax the boundary.

## Testing (Vitest + Spectator)

```typescript
// libs/<domain>/api/src/lib/<domain>.store.spec.ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DomainStore } from './<domain>.store';

describe('DomainStore', () => {
  let spectator: SpectatorService<DomainStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: DomainStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads items and updates the signal', () => {
    spectator.service.load();

    const req = httpMock.expectOne('/api/<domain>');
    req.flush([{ id: '1' }]);

    expect(spectator.service.items()).toHaveLength(1);
    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message when the request fails', () => {
    spectator.service.load();

    const req = httpMock.expectOne('/api/<domain>');
    req.error(new ProgressEvent('error'));

    expect(spectator.service.error()).toBeTruthy();
    expect(spectator.service.loading()).toBe(false);
  });
});
```

For Variant B, `httpResource` still issues a real HTTP call under the hood
on construction, so `HttpTestingController` works the same way — there's
just no `load()` method to call first; assert against the request fired by
instantiating the service, and use `refresh()` if the test needs a second
call.

## When this pattern is the wrong call

Read `references/tradeoffs.md` before recommending this for a domain with
heavy undo/redo, an audit-log requirement, or a team large enough to need
one-way data flow enforced by the compiler rather than by convention. This
skill's job is to make the *default* case lighter, not to be dogmatic about
avoiding NgRx everywhere.
