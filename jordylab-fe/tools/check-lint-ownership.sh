#!/usr/bin/env bash
# Fails when a lint rule is enabled in both ESLint and Oxlint, or when Oxlint touches what ESLint must own (spec 012 FR-011/FR-012).
#   tools/check-lint-ownership.sh
# ESLint owns every `@angular-eslint/*` rule, the Angular template rules, `@nx/enforce-module-boundaries`, the core rules and the
# typescript-eslint rules; Oxlint owns only the `oxc/*` and `unicorn/*` rules ESLint does not have (see .oxlintrc.json).
# The resolved rule sets come from `eslint --print-config` (one representative TypeScript file per kind of project) and
# `oxlint --print-config`; rule names are compared after mapping `@typescript-eslint/x` to `typescript/x`.
set -u
FRONTEND_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$FRONTEND_ROOT" || exit 2

# "<project directory> <file inside it>": Nx lints from each project's directory, where that project's own eslint.config.mjs
# (with the Angular and template presets) applies; the root config alone has no Angular rules.
ESLINT_SAMPLES=(
  "apps/jordylab src/main.ts"
  "libs/fna/ui src/lib/article-list/article-list.component.ts"
  "libs/shared/auth src/lib/pkce.ts"
  "libs/gamecatalog/api src/index.ts"
)
for sample in "${ESLINT_SAMPLES[@]}"; do
  project="${sample%% *}"
  file="${sample#* }"
  [[ -f "$project/$file" ]] || { echo "ownership check: sample file missing: $project/$file" >&2; exit 2; }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

index=0
for sample in "${ESLINT_SAMPLES[@]}"; do
  index=$((index + 1))
  project="${sample%% *}"
  file="${sample#* }"
  ( cd "$project" && "$FRONTEND_ROOT/node_modules/.bin/eslint" --print-config "$file" >"$WORK/eslint-$index.json" 2>/dev/null ) \
    || { echo "ownership check: eslint --print-config failed for $project/$file" >&2; exit 2; }
done
node_modules/.bin/oxlint -c .oxlintrc.json --print-config >"$WORK/oxlint.json" 2>/dev/null \
  || { echo "ownership check: oxlint --print-config failed" >&2; exit 2; }

python3 - "$WORK" <<'PY'
import glob, json, sys

work = sys.argv[1]

def eslint_enabled(path):
    rules = json.load(open(path)).get("rules", {})
    enabled = set()
    for name, value in rules.items():
        severity = value[0] if isinstance(value, list) else value
        if severity in (1, 2, "warn", "error"):
            enabled.add(name.replace("@typescript-eslint/", "typescript/", 1))
    return enabled

def oxlint_enabled(path):
    rules = json.load(open(path)).get("rules", {})
    return {name for name, value in rules.items()
            if (value[0] if isinstance(value, list) else value) in ("warn", "error", "deny")}

eslint_rules = set()
for path in glob.glob(f"{work}/eslint-*.json"):
    eslint_rules |= eslint_enabled(path)
oxlint_rules = oxlint_enabled(f"{work}/oxlint.json")

problems = []
for rule in sorted(eslint_rules & oxlint_rules):
    problems.append(f"enabled in both linters: {rule}")
for rule in sorted(oxlint_rules):
    if rule.startswith(("@angular-eslint/", "@nx/", "@angular/")):
        problems.append(f"Oxlint must not own an ESLint-only rule: {rule}")
if not any(rule.startswith("@angular-eslint/") for rule in eslint_rules):
    problems.append("ESLint no longer enables any @angular-eslint rule (check the config)")
if "@nx/enforce-module-boundaries" not in eslint_rules:
    problems.append("ESLint no longer enables @nx/enforce-module-boundaries")

if problems:
    print("lint ownership check FAILED:")
    print("\n".join("  " + p for p in problems))
    sys.exit(1)
print(f"lint ownership check passed: {len(eslint_rules)} ESLint rules, {len(oxlint_rules)} Oxlint rules, none shared")
PY
