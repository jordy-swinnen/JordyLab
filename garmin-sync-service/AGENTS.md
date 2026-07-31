# Commands

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest                   # Run all tests
python -m pytest --cov=src         # Tests with coverage
ruff check . && ruff format .      # Lint + format
```

Use `ruff` for both linting and formatting — no `black`, `flake8`, or `isort`.

# Python Code Style

- Target Python 3.12+
- Use type hints on all function signatures and return types
- Use `dataclass` or Pydantic `BaseModel` for data structures — no raw dicts for domain objects
- Prefer `pathlib.Path` over `os.path`
- Use f-strings for formatting
- Use `logging` module — never `print()` for operational output
- Organize with `src/` layout: `src/` for source, `tests/` at project root
- Use `ruff` for linting and formatting
- Write Google-style docstrings for public functions
- Use `psycopg` v3, not `psycopg2`

# Testing

- Use `pytest` with fixtures and `parametrize`
- Use `pytest-mock` (mocker fixture) over `unittest.mock` directly
- Name test files `test_<module>.py`, test functions `test_<scenario>()`
- Use plain `assert` with descriptive messages
- Store raw Garmin API responses as JSON fixtures in `tests/fixtures/`

# garmin-sync-service

- Data pipeline only — syncs, transforms, and writes. Business logic lives in jordylab-be
- Write to the `garmin` schema only
- Use upsert-only writes and idempotent re-runs — not blind inserts
- Every Garmin table stores raw API response in a `raw_json JSONB` column
- Flyway in jordylab-be owns all DDL — never create/alter tables from Python
- Garmin Connect API is unofficial — tokens expire unpredictably
- Sync intervals should be conservative (15–30 min) to avoid rate limiting
