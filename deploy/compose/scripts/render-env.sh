#!/usr/bin/env bash
# Runner side of the Compose deploy (spec 020, contracts/env-contract.md, research R6).
#
# Decrypts the production secrets with SOPS and prints the environment file to standard output, one
# NAME='value' line per variable. The deploy workflow pipes this output straight into SSH, so the values never
# reach a log. Error messages name a variable, never its value.
#
# Needs SOPS_AGE_KEY (the age private key, from the production environment secret), sops and python3 with PyYAML.
# SECRETS_FILE overrides the encrypted file; it exists for tests only.
set -euo pipefail

readonly secrets_file="${SECRETS_FILE:-deploy/k8s/overlays/prod/secrets.sops.yaml}"

# The Python program that reads the decrypted multi-document YAML on standard input. Only variable names appear
# in its error messages.
render_program=$(
  cat <<'PY'
import sys

try:
    import yaml
except ImportError:
    sys.exit("render-env: PyYAML is required (python3 -m pip install pyyaml)")

# Settings that are not secret. They live here so a change is reviewed in git (contracts/env-contract.md).
LITERALS = [
    ("SPRING_PROFILES_ACTIVE", "prod"),
    ("KEYCLOAK_URL", "https://jordylab.be/auth"),
    ("KEYCLOAK_INTERNAL_URL", "http://keycloak:8080/auth"),
    ("POSTGRES_DB", "jordylab"),
    ("POSTGRES_URL", "jdbc:postgresql://postgres:5432/jordylab"),
    ("JORDYLAB_CORS_ALLOWED_ORIGINS", "https://jordylab.be,https://localhost"),
    ("JORDYLAB_APP_BASE_URL", "https://jordylab.be"),
    ("NTFY_BASE_URL", "https://jordylab.be/ntfy"),
    ("LANGFUSE_ENVIRONMENT", "production"),
    ("LANGFUSE_CAPTURE_CONTENT", "true"),
    ("SPRINGDOC_API_DOCS_ENABLED", "false"),
    ("SPRINGDOC_SWAGGER_UI_ENABLED", "false"),
    ("KC_DB_URL", "jdbc:postgresql://postgres:5432/jordylab?currentSchema=keycloak"),
    ("KC_DB_SCHEMA", "keycloak"),
    ("KC_HOSTNAME", "https://jordylab.be/auth"),
    ("KC_HTTP_RELATIVE_PATH", "/auth"),
    ("KC_PROXY_HEADERS", "xforwarded"),
    ("KC_HTTP_ENABLED", "true"),
    ("KC_PROXY_TRUSTED_ADDRESSES", "172.28.0.0/16"),
    ("PRODUCTION_DOMAIN", "jordylab.be"),
]

# (variable, Secret, key in that Secret, required). A required variable fails the render when empty.
# Optional variables are API keys and tokens that the application already treats as unset when empty.
SECRET_VALUES = [
    ("POSTGRES_USER", "jordylab-db-app", "username", True),
    ("POSTGRES_PASSWORD", "jordylab-db-app", "password", True),
    ("KC_DB_USERNAME", "jordylab-db-keycloak", "username", True),
    ("KC_DB_PASSWORD", "jordylab-db-keycloak", "password", True),
    ("IDENTITY_BOOTSTRAP_ADMIN_USERNAME", "jordylab-secrets", "IDENTITY_BOOTSTRAP_ADMIN_USERNAME", True),
    ("IDENTITY_BOOTSTRAP_ADMIN_PASSWORD", "jordylab-secrets", "IDENTITY_BOOTSTRAP_ADMIN_PASSWORD", True),
    ("POSTGRES_SUPERUSER_PASSWORD", "jordylab-secrets", "POSTGRES_SUPERUSER_PASSWORD", True),
    ("PGBACKREST_REPO1_S3_KEY", "jordylab-secrets", "S3_ACCESS_KEY", True),
    ("PGBACKREST_REPO1_S3_KEY_SECRET", "jordylab-secrets", "S3_SECRET_KEY", True),
    ("NTFY_TOPIC", "jordylab-secrets", "NTFY_TOPIC", True),
    ("NTFY_TOKEN", "jordylab-secrets", "NTFY_TOKEN", False),
    ("LINEAR_API_KEY", "jordylab-secrets", "LINEAR_API_KEY", False),
    ("LANGFUSE_BASE_URL", "jordylab-secrets", "LANGFUSE_BASE_URL", False),
    ("LANGFUSE_PUBLIC_KEY", "jordylab-secrets", "LANGFUSE_PUBLIC_KEY", False),
    ("LANGFUSE_SECRET_KEY", "jordylab-secrets", "LANGFUSE_SECRET_KEY", False),
    ("ANTHROPIC_API_KEY", "jordylab-secrets", "ANTHROPIC_API_KEY", False),
    ("OPENROUTER_API_KEY", "jordylab-secrets", "OPENROUTER_API_KEY", False),
    ("STEAM_WEB_API_KEY", "jordylab-secrets", "STEAM_WEB_API_KEY", False),
    ("STEAM_ID", "jordylab-secrets", "STEAM_ID", False),
    ("IGDB_CLIENT_ID", "jordylab-secrets", "IGDB_CLIENT_ID", False),
    ("IGDB_CLIENT_SECRET", "jordylab-secrets", "IGDB_CLIENT_SECRET", False),
    ("THEGAMESDB_API_KEY", "jordylab-secrets", "THEGAMESDB_API_KEY", False),
]


def fail(message):
    sys.exit(f"render-env: {message}")


def format_line(name, value):
    # The environment file is read with single quotes, which take the value literally.
    if "'" in value or "\n" in value:
        fail(f"{name} contains a quote or a newline, which the environment file cannot carry")
    return f"{name}='{value}'"


def read_secrets():
    secrets = {}
    for document in yaml.safe_load_all(sys.stdin):
        if document:
            secrets[document["metadata"]["name"]] = document.get("stringData") or {}
    return secrets


def main():
    secrets = read_secrets()
    lines = [format_line(name, value) for name, value in LITERALS]
    for name, secret_name, key, required in SECRET_VALUES:
        if secret_name not in secrets:
            fail(f"Secret {secret_name} is missing from the secrets file")
        if key not in secrets[secret_name]:
            fail(f"key {key} is missing from Secret {secret_name}")
        value = secrets[secret_name][key]
        if value is None:
            value = ""
        if isinstance(value, bool) or not isinstance(value, (str, int)):
            fail(f"{name} is not a plain string in the secrets file")
        value = str(value)
        if required and value == "":
            fail(f"{name} is empty but required")
        lines.append(format_line(name, value))
    sys.stdout.write("\n".join(lines) + "\n")


main()
PY
)
readonly render_program

if [[ -z "${SOPS_AGE_KEY:-}" ]]; then
  echo "render-env: SOPS_AGE_KEY is not set" >&2
  exit 1
fi

sops --decrypt --output-type yaml "$secrets_file" | python3 -c "$render_program"
