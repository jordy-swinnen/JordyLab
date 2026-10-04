# Contract: lint ownership check

`jordylab-fe/tools/check-lint-ownership.sh` (run in CI before the Oxlint step, and by hand).

- Input: the resolved Oxlint rule set and the resolved ESLint rule set for a representative `.ts` file per library type.
- Output: lists any rule enabled in both; exits `1` if the list is not empty, `0` otherwise.
- Allowed overlap: none. Rule ids are compared after normalising the plugin prefix (`@typescript-eslint/x` = `typescript/x`).
- ESLint must still own: every `@angular-eslint/*` rule, template rules, `@nx/enforce-module-boundaries`.
- Removal: deleted together with `.oxlintrc.json` in the one-commit removal (FR-022).
