---
paths:
  - "**/*.py"
---

# Python (JordyLab sidecars)

Cross-cutting conventions for every Python service in this repo. Service-specific concerns (DB driver choice, sync model, auth, scheduling) live in `<service>/AGENTS.md` and intentionally are NOT duplicated here.

Applies to `garmin-sync-service/`, `gamecatalog-sync-service/`, and any future Python service under this repo.

## Tooling

- **Python 3.12+** — `requires-python = ">=3.12"` in `pyproject.toml`; `target-version = "py312"` in `ruff`
- **`uv` is the preferred venv and dependency manager for new services** — `uv venv .venv --python 3.12` then `uv pip install -r requirements.txt`. Older `python -m venv` + `pip` is acceptable for existing services; do not mix
- **`ruff` does both linting and formatting** — replaces `black`, `flake8`, `isort`. Run `ruff check . && ruff format .` before committing
- **`pytest` for tests** — not `unittest` directly. `python -m pytest` from the service root, with `pythonpath = ["src"]` in `pyproject.toml` so tests import the `src/` package
- **`src/` layout** — source in `src/<package>/`, tests at `tests/` root. Never flat-layout for new services
- No `poetry`, `pyenv`, `black`, `flake8`, `isort`, `pipenv` — pick one of the two approved paths above

## Language & syntax

- **Type hints on every function signature and return type** — non-negotiable. No untyped public functions, ever
- Use the most up-to-date 3.12 syntax (`type | None`, `Self`, `type` statements, `match` where it reads cleaner)
- `from __future__ import annotations` at the top of every module — lazy evaluation, forward refs
- **Full descriptive names — no abbreviations or single-letter identifiers** (matches the master prompt)
- Public functions/methods first, private last — group related logic for cohesion
- Space before every `return`
- Early returns over deep nesting; self-explanatory code over comments

## Data structures

- **`dataclass` (frozen=True) or Pydantic v2 `BaseModel` for structured data — no raw `dict` for domain objects**
- Pydantic v2 over v1: `BaseModel.model_validate(...)`, `model_dump(...)`, not the v1 `parse_obj` / `dict`
- `pathlib.Path` over `os.path` — never `os.path.join`
- f-strings for formatting — no `%`, no `.format()`
- `dataclasses.replace(...)` for non-destructive mutation rather than copying fields by hand
- Prefer `tuple[...]` and `frozenset[...]` for immutable collections in type hints; `from collections.abc import Iterable, Mapping, Sequence` not the typing-module aliases

## Logging & I/O

- `logger = logging.getLogger(__name__)` at the top of every module — never `print()` for operational output
- `print()` is fine only in `__main__` entrypoints, REPL scratch, or pytest debug output
- Secrets (tokens, passwords, API keys) must never appear in log messages, error strings, or exception messages — redact or reference the env-var name only. See "Secrets" below

## Function design

- Methods: short, single responsibility, pure where possible
- Builders for complex objects; prefer immutability (`frozen=True` dataclasses, Pydantic models, returned new objects)
- Expressive constructs over clever tricks; KISS, YAGNI, DRY, Clean Code
- Exceptions: intentional and sparse. Fail fast with clear errors — no silent failures, no swallowed exceptions
- Named functions for non-trivial logic — no anonymous `lambda`s in module scope

## Error handling

- Fail fast at the boundary. If a required env var is missing, raise on first read with a message naming the var — do not proceed with a `None` default
- Custom exception classes per failure mode (e.g. `ServerError(status_code=...)`) rather than reusing `RuntimeError` / `ValueError` for application-level errors
- Always chain: `raise NewError("...") from original_error`
- Catch the narrowest exception type that applies. Never bare `except:` or `except Exception:` at module boundaries unless re-raising

## Testing

- `pytest` with fixtures and `parametrize`; descriptive test names explaining the scenario
- `@pytest.mark.django_db` and similar framework-specific markers only where the service actually uses that framework
- `pytest-mock` (the `mocker` fixture) over `unittest.mock` directly
- `respx` to mock `httpx` calls — never spin up a real server in unit tests
- `tmp_path` for filesystem fixtures (scanners, state files, cached JSON)
- Test files: `test_<module>.py`. Test functions: `test_<scenario>()`
- Plain `assert` with a descriptive message: `assert result.status_code == 200, f"expected 200, got {result.status_code}"`
- Store raw external responses as JSON fixtures under `tests/fixtures/` for replay
- One logical assertion per test where possible; `assertSoftly`-style soft assertions via plain `assert` blocks where multi-field checks are clearer
- `respx` examples:
  ```python
  import respx
  from httpx import Response

  @respx.mock
  async def test_fetch_game() -> None:
      respx.get("https://example.test/games/1").mock(return_value=Response(200, json={"id": 1}))
      ...
  ```

## Architecture (JordyLab Python services)

- **Python services in this repo are sidecars, not standalone apps.** Two patterns exist:
  1. **Data pump** (e.g. `garmin-sync-service/`) — pulls from an external API, writes to a single Postgres schema. All writes are upserts (`INSERT ... ON CONFLICT (...) DO UPDATE`) for idempotent re-runs. Stores the raw API response in a `raw_json JSONB` column when the service follows this pattern
  2. **HTTPS-push agent** (e.g. `gamecatalog-sync-service/`) — scans a local resource, submits JSON snapshots to a backend endpoint over bearer-token-authenticated HTTPS. No database driver
- **Business logic lives in `jordylab-be`, not in Python.** Sidecars fetch, transform, and persist. They do not own AI, RAG, validation rules, or domain decisions
- **DDL is owned by Flyway in `jordylab-be`.** Python services never `CREATE TABLE`, `ALTER TABLE`, or run DDL — even when they own the schema's data. The schema is created and migrated by the backend; Python only writes rows
- **Each sidecar writes to exactly one schema.** A service that owns the `garmin` schema does not write to `gamecatalog` or `finance`. A push-agent like `gamecatalog-sync-service` does not touch any schema
- **JordyBox is the schedule, not a runtime.** Sync intervals are conservative (15–30 min). Use `cron` or a systemd timer, not a long-running loop with `time.sleep`. This avoids hanging processes when the host reboots
- **No global mutable state.** Module-level constants are fine; module-level mutable caches are not. Pass state explicitly or through well-named factories

## HTTP (httpx) — when a service makes outbound calls

- `httpx.Client` (sync) or `httpx.AsyncClient` — match the surrounding code's async-ness
- Set `base_url` once on the client, strip a trailing `/` to avoid double-slash paths
- Set `Authorization` once via `headers=...` on the client, not per-request
- Set a `timeout=httpx.Timeout(N)` on the client — never let `httpx` default to "no timeout"
- Wrap the client in a context manager (`with httpx.Client(...) as client:`) or implement `__enter__` / `__exit__` on a wrapper class
- Catch `httpx.HTTPError` at the call boundary; raise a domain-specific exception (e.g. `ServerError`) with optional `status_code`
- Validate response bodies with Pydantic `model_validate(response.json())` — never trust the wire shape
- For non-2xx, raise with the method, path, and status code in the message: `f"{method} {path} returned HTTP {response.status_code}"`

## Secrets

From the root `AGENTS.md`:

- **Never echo, log, or print secret values** — `ANTHROPIC_API_KEY`, `GAMECATALOG_INGEST_TOKEN`, `POSTGRES_PASSWORD`, and any other key/token/password. This applies to chat output, file contents, diffs, screenshots, and code
- When asked to "show" a `.env` or a key, redact with `<redacted>` or show only the variable name
- **Verify secrets work by behavior** (does the call return 200?), not by reading the value
- Load secrets from env at startup; fail-fast on missing required vars with a message naming the variable
- Never commit `.env` with real values. Commit a `.env.example` with placeholders only
