# When manual signal stores are (and aren't) the right call

## What you gain over NgRx

- No actions/reducers/effects/selectors boilerplate — one file per domain
  instead of five.
- Fully inside Angular's own reactivity graph — plays natively with
  zoneless change detection and OnPush, no `Store.select()` indirection.
- Lower onboarding cost — it's a service; anyone who knows Angular DI
  already knows how to use it.
- Matches signals-first, `inject()`-first conventions if the codebase has
  already standardized on those.

## What you give up

- **No Redux DevTools time-travel or action log.** If a domain needs an
  audit trail of state changes for debugging, that has to be built by hand
  — e.g. an `effect()` that logs signal changes in dev mode.
- **No structural enforcement of one-way data flow.** NgRx makes illegal
  state mutation impossible by construction; a signal store enforces it
  only by convention (`#private` + `.asReadonly()`). That's a fine tradeoff
  for a solo project or a small team with consistent review habits. It's a
  worse tradeoff on a larger team where someone will eventually expose a
  writable signal by accident and nothing stops them at compile time.
- **No built-in undo/replay.** Fine for typical list/detail/briefing-style
  domains; would matter more for something like a multi-step editor with
  an undo stack.

## Resource API stability

`resource()`, `rxResource()`, and `httpResource()` have moved between
experimental and stable status across recent Angular minor versions —
don't assume the status quoted in any given blog post or tutorial still
holds for the exact version installed. Check the project's
`@angular/core` version against the official changelog before depending on
Variant B for anything beyond a prototype. If it's still flagged
experimental in the installed version, use Variant A instead — the manual
signal approach has no dependency on that API's maturity at all.

## The actual decision rule

Default to the manual-signals variant (Variant A) unless the domain is
genuinely read-only. Most real domains pick up at least one mutation over
time (a "mark as read," a local optimistic add, a retry), and migrating
from `httpResource()` to manual signals later is more churn than starting
with manual signals and only reaching for `httpResource()` when a domain
is provably read-only for its full lifetime.
