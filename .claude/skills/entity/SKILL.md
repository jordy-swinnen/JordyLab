---
name: entity
description: Canonical JPA entity structure for JordyLab — builder pattern, Preconditions in build(), domain events, and required test coverage.
---

# Entity Skill

Read this file in full before creating any JPA entity.

## Canonical structure

```java
@Entity
@Table(name = "some_entity")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SomeEntity extends AbstractAggregateRoot<SomeEntity> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    // @Setter only when the type is immutable (String, UUID, primitive, etc.)
    @Setter
    private String description;

    // Named mutation method — never a raw setter for mutable state
    public void updateName(String newName) {
        Preconditions.checkArgument(StringUtils.hasText(newName), "name must not be blank");
        this.name = newName;
        registerEvent(new SomeEntityNameUpdated(this.id, newName));
    }

    // Lombok generates all .field() methods — only build() is hand-written
    public static class SomeEntityBuilder {
        public SomeEntity build() {
            Preconditions.checkArgument(StringUtils.hasText(name), "name is required");
            if (id == null) id = UUID.randomUUID();

            return new SomeEntity(id, name, description);
        }
    }
}
```

## Canonical test structure

Every entity must ship with a corresponding test class. The test is not optional —
it is part of the definition of done for any entity.

```java
class SomeEntityTest {

    @Test
    void buildSomeEntity() {
        SomeEntity entity = SomeEntity.builder()
                .name("test name")
                .build();

        assertThat(entity.getName()).isEqualTo("test name");
        assertThat(entity.getId()).isNotNull();
    }

    // One test per required field — null case
    @Test
    void buildWithoutName() {
        assertThatThrownBy(() -> SomeEntity.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // One test per required String field — blank case
    @Test
    void buildWithBlankName() {
        assertThatThrownBy(() -> SomeEntity.builder().name(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(SomeEntity.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
```

## Rules to follow exactly

**Entity class:**
- Replace `SomeEntity` / `some_entity` / `SomeEntityBuilder` with the actual entity name
- `id` is always `UUID` — never `Long` or any other type
- `@Getter` is class-level — never repeat it on individual fields
- Only add `@Setter` to fields whose type is immutable (String, UUID, primitives) — everything
  else gets a named mutation method
- `Preconditions.checkArgument` lives exclusively in `build()` — nowhere else
- `registerEvent(...)` is called inside mutation methods — never exposed directly
- Audit fields (`createdDate`, `lastModifiedDate`) are inherited — never declare them here
- The partial builder class name must exactly match `<EntityName>Builder` for Lombok to merge it

**equals/hashCode:**
- Never write `equals`/`hashCode` by hand — never use Lombok `@EqualsAndHashCode`
- `equals`/`hashCode` are inherited from `BaseEntity` — never override them in concrete entities
- Correctness is verified by the `EqualsVerifier` test instead
- Always suppress three warnings: `SURROGATE_KEY` (equality based on @Id field only),
  `IDENTICAL_COPY_FOR_VERSIONED_ENTITY` (null id → not reflexive, by design),
  and `STRICT_HASHCODE` (hashCode intentionally constant at `getClass().hashCode()`)

**Entity test class:**
- Cover the happy path build with assertions on every field set
- Cover null and blank cases separately for every required field
- The `equals()` test uses `EqualsVerifier` — never assert equals/hashCode manually
- Test class is package-private, lives in the same package as the entity under `src/test/`