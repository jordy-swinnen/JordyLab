---
name: test-builder
description: Canonical TestBuilder fixture class structure for JordyLab — @UtilityClass, DEFAULT_* constants, aDefault/a builder methods.
---

# TestBuilder Skill

Read this file in full before creating any test fixture class.

## Canonical structure

```java
@UtilityClass
class SomeObjectTestBuilder {

    // Public static fields — expose all defaults here for use in assertions
    public static final UUID DEFAULT_ID = UUID.fromString("4f675aad-fa21-4b3b-9555-1b698b4e0c0a");
    public static final String DEFAULT_NAME = "John Example";
    public static final Instant DEFAULT_CREATED_DATE = Instant.parse("2025-01-13T00:00:00Z");

    // Returns a fully built default object — use when the test does not need customisation
    public static SomeObject aDefaultSomeObject() {
        return aSomeObject().build();
    }

    // Returns a pre-filled builder — use when a test needs to override one or more fields
    public static SomeObject.SomeObjectBuilder aSomeObject() {
        return SomeObject.builder()
                .id(DEFAULT_ID)
                .name(DEFAULT_NAME)
                .createdDate(DEFAULT_CREATED_DATE);
    }
}
```

## Rules to follow exactly

- Class is package-private (no `public` modifier), annotated with `@UtilityClass`
- All default values are `public static final` fields declared at the top of the class —
  never inline literals inside the builder method
- Assertions in tests reference these constants directly, so a value change propagates
  automatically without hunting through test code
- `aDefault{Object}()` calls `a{Object}().build()` — never duplicates field assignments
- `a{Object}()` returns the Lombok builder pre-filled with all defaults
- Default values must be fixed and realistic — hardcoded UUIDs, fixed `Instant` strings,
  meaningful strings — never random, never `Instant.now()`
- Replace `SomeObject` with the actual class name throughout, including the builder method names