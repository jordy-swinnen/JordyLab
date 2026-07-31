---
name: modularity-check
description: Run Spring Modulith boundary tests, parse violations, explain what broke, suggest a fix, and re-verify.
---

# Modularity Check

## Steps

1. Run the modularity tests:
   ```bash
   cd jordylab-be && ./gradlew :test --tests "*ModularityTests*" 2>&1
   ```

2. If tests pass, report success and stop.

3. If tests fail, parse the output for violation details:
   - Which module is being accessed?
   - Which module is the offender?
   - What class/package boundary was crossed?

4. Explain the violation in plain language:
   - "Module X is importing an internal class from module Y"
   - Reference the Spring Modulith rule: module root = public API, sub-packages = internal

5. Suggest a fix:
   - Move the class to the module's root (public API) package
   - Create a facade/DTO in the root package
   - Use `@NamedInterface` if this is a legitimate shared sub-package
   - Re-route through an event or public facade

6. After applying the fix, re-run the tests to verify:
   ```bash
   cd jordylab-be && ./gradlew :test --tests "*ModularityTests*" 2>&1
   ```

7. Report final status: pass/fail with summary of changes made.
