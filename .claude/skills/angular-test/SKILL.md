---
name: angular-test
description: Canonical Vitest + Spectator testing conventions for JordyLab Angular components and signal stores — mocking with useValue/vi.fn, mock fixture placement, and spec structure. Read before writing or reviewing any .spec.ts file in jordylab-fe.
---

# Angular Test Skill

Read this file in full before writing or reviewing a `.spec.ts` file in `jordylab-fe`.

## Mocking a dependency

Never write a hand-rolled mock class and provide it with `useClass`:

```typescript
// WRONG — do not do this
class FnaApiServiceMock {
  private articlesSubject = new Subject();
  getArticles = vi.fn(() => this.articlesSubject.asObservable());
}

providers: [{ provide: FnaApiService, useClass: FnaApiServiceMock }]
```

Instead, provide a plain object via `useValue`, with `vi.fn()` for methods:

```typescript
// RIGHT
const load = vi.fn();
const providers = [{ provide: SomeService, useValue: { load } }];
```

### Mocking a signal store

A signal store (see `/angular-signal-store`) exposes readonly signals and imperative methods.
Back the signals with real `signal(...)` instances held at `describe` scope, so tests can drive
state directly with `.set(...)` — don't try to fake a `Signal` any other way:

```typescript
describe('ArticleListComponent', () => {
  const articles = signal<ArticleSummary[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);

  const storeMock = {
    articles: articles.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load: vi.fn(),
  };

  const createComponent = createComponentFactory({
    component: ArticleListComponent,
    providers: [{ provide: ArticleStore, useValue: storeMock }],
  });

  let spectator: Spectator<ArticleListComponent>;

  beforeEach(() => {
    articles.set([]);
    loading.set(true);
    error.set(null);
    spectator = createComponent();
  });

  it('displays populated articles', () => {
    loading.set(false);
    articles.set([anArticleSummaryMock({ title: 'ECB holds rates' })]);
    spectator.detectChanges();

    expect(spectator.query('h3')).toHaveText('ECB holds rates');
  });
});
```

**Never write `spectator.inject(Token) as unknown as SomeMock`.** If a test needs to assert on
injected state or drive it, hold a reference to the mock's own signals/spies from `describe` scope
*before* creating the component — don't inject the DI-resolved instance back out and cast it. The
cast is a symptom that the mock was built the wrong way (a class instead of a plain object), not a
technique to reach for.

## Component creation

Create the component **once** in `beforeEach`, not separately inside every `it()`:

```typescript
// WRONG
it('renders x', () => {
  const spectator = createComponent();
  ...
});
it('renders y', () => {
  const spectator = createComponent();
  ...
});

// RIGHT
let spectator: Spectator<MyComponent>;

beforeEach(() => {
  // reset any shared signal/spy state here first
  spectator = createComponent();
});
```

If tests share mutable state (the signals backing a store mock), reset that state at the top of the
same `beforeEach`, before calling `createComponent()`.

## Mock fixture files

Fixture data for a model interface lives in `libs/<domain>/api/src/lib/mocks/<interface>.model.mock.ts`
— one file per interface, named after it, exporting a factory function that accepts partial overrides:

```typescript
// libs/fna/api/src/lib/mocks/article-summary.model.mock.ts
import { ArticleSummary } from '../fna.models';

export function anArticleSummaryMock(overrides: Partial<ArticleSummary> = {}): ArticleSummary {
  return {
    id: '1',
    title: 'ECB holds rates steady',
    url: 'https://example.com/1',
    publishedAt: '2026-08-01T06:00:00Z',
    feedName: 'ECB Press Releases',
    ...overrides,
  };
}
```

Export the factory from the lib's `index.ts` barrel alongside the store/model exports — this
workspace uses a single barrel per lib, not a separate `testing` entry point.

## Imports

Specs import via the barrel (`@jordylab-fe/<domain>/<layer>`), never a deep-relative path into
another lib (`../../other-lib/src/lib/...`). A relative import within the *same* lib (`./sibling.component`)
is fine.

## Signal store specs

A signal store's own spec drives it through `HttpTestingController`, per `/angular-signal-store`'s
testing section — assert on the store's public signals directly, not through a consuming component:

```typescript
describe('ArticleStore', () => {
  let spectator: SpectatorService<ArticleStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: ArticleStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads articles on construction', () => {
    const req = httpMock.expectOne('/api/fna/articles');
    req.flush([anArticleSummaryMock()]);

    expect(spectator.service.articles()).toHaveLength(1);
  });
});
```

If the store fetches eagerly in its constructor (the common case for this codebase), the first
`expectOne(...)` in each test picks up the request fired by `createService()` itself — there is no
separate `load()` call needed before the first assertion.
