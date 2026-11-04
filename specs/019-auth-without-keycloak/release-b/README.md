# Release B — after identity is verified

This folder holds the schema drop that must **not** ship with release A. Release A creates `identity` and copies the
persons and `app-gamecatalog` grants from the `keycloak` schema, while Keycloak stays running (idle) for rollback.
Only after the owner verifies the migrated accounts, every person has reset their password and enrolled a passkey, and
the verified backup exists, move `V20261103005__drop_keycloak_schema.sql` into
`jordylab-be/src/main/resources/db/migration/` and ship release B. Release B also removes the Keycloak runtime
artifacts (the Compose service and `/auth` route, the image build, the realm files and the theme).
