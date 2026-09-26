"""Client configuration and local state locations.

Service endpoints normally arrive from the constants header rendered into the
frozen client. In package/dev mode they can be supplied by environment
variables or a ``service`` block in ``config.json``. The only user-facing
config file content is an optional per-library ``roots`` map.
"""

import json
import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_KEYCLOAK_URL = "http://localhost:8180"
DEFAULT_REALM = "jordylab"
DEFAULT_CLIENT_ID = "gamecatalog-script"
DEFAULT_BACKEND_URL = "http://localhost:8080"
DEFAULT_SCAN_ENDPOINT = "/api/gamecatalog/ingest/scan"

_ENV_KEYS = {
    "keycloak_url": "JORDYLAB_SCAN_KEYCLOAK_URL",
    "realm": "JORDYLAB_SCAN_REALM",
    "client_id": "JORDYLAB_SCAN_CLIENT_ID",
    "backend_url": "JORDYLAB_SCAN_BACKEND_URL",
    "scan_endpoint": "JORDYLAB_SCAN_ENDPOINT",
}

SUPPORTED_LIBRARY_TYPES = ("STEAM", "EMUDECK")

# Built at runtime so the frozen artifact contains the placeholder marker only
# inside its rendered constants header.
PLACEHOLDER_MARK = "$" + "{"


@dataclass
class AppConfig:
    library_type: str
    keycloak_url: str = DEFAULT_KEYCLOAK_URL
    realm: str = DEFAULT_REALM
    client_id: str = DEFAULT_CLIENT_ID
    backend_url: str = DEFAULT_BACKEND_URL
    scan_endpoint: str = DEFAULT_SCAN_ENDPOINT

    def check_endpoint(self):
        base = self.scan_endpoint.rsplit("/", 1)[0]

        return base + "/check"


def config_dir():
    override = os.environ.get("JORDYLAB_SCAN_CONFIG_DIR")
    if override:
        return Path(override)

    return Path.home() / ".config" / "jordylab" / "scan"


def state_dir():
    override = os.environ.get("JORDYLAB_SCAN_STATE_DIR")
    if override:
        return Path(override)

    return Path.home() / ".local" / "state" / "jordylab-scan"


def config_file():
    return config_dir() / "config.json"


def token_file():
    return config_dir() / "token.json"


def machine_id_file():
    return config_dir() / "machine-id"


def service_config_file():
    return config_dir() / "service.json"


def load_config_json(path=None):
    path = Path(path) if path else config_file()
    if not path.is_file():
        return {}
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return {}
    if isinstance(data, dict):
        return data

    return {}


def configured_roots(library_type, path=None):
    """Explicit roots from ``config.json`` for a library type (may be empty)."""
    data = load_config_json(path)
    roots = data.get("roots")
    if not isinstance(roots, dict):
        return []
    values = roots.get(library_type)
    if not isinstance(values, list):
        return []

    return [str(value) for value in values if isinstance(value, str) and value]


def _clean(value):
    if not value:
        return None
    if PLACEHOLDER_MARK in str(value):
        return None

    return str(value)


def resolve_service(library_type, constants=None, config_data=None, env=None):
    """Build an :class:`AppConfig`.

    Precedence: rendered constants > ``service`` block in config.json >
    environment variables > built-in defaults.
    """
    constants = constants or {}
    config_data = config_data or {}
    env = env if env is not None else os.environ
    service = config_data.get("service") if isinstance(config_data.get("service"), dict) else {}

    def pick(constant_key, config_key, env_key, default):
        for candidate in (
            _clean(constants.get(constant_key)),
            _clean(service.get(config_key)),
            _clean(env.get(env_key)),
        ):
            if candidate is not None:
                return candidate

        return default

    return AppConfig(
        library_type=library_type,
        keycloak_url=pick(
            "KEYCLOAK_URL", "keycloak_url", _ENV_KEYS["keycloak_url"], DEFAULT_KEYCLOAK_URL
        ),
        realm=pick("REALM", "realm", _ENV_KEYS["realm"], DEFAULT_REALM),
        client_id=pick("CLIENT_ID", "client_id", _ENV_KEYS["client_id"], DEFAULT_CLIENT_ID),
        backend_url=pick(
            "BACKEND_URL", "backend_url", _ENV_KEYS["backend_url"], DEFAULT_BACKEND_URL
        ),
        scan_endpoint=pick(
            "SCAN_ENDPOINT", "scan_endpoint", _ENV_KEYS["scan_endpoint"], DEFAULT_SCAN_ENDPOINT
        ),
    )


def normalize_library_type(value):
    """Upper-case and validate a library type, raising ``ValueError`` if bad."""
    if value is None:
        raise ValueError("library type is required")
    normalized = str(value).strip().upper()
    if normalized not in SUPPORTED_LIBRARY_TYPES:
        raise ValueError("library type must be one of " + ", ".join(SUPPORTED_LIBRARY_TYPES))

    return normalized


def ensure_private_dir(path=None):
    target = Path(path) if path else config_dir()
    target.mkdir(mode=0o700, parents=True, exist_ok=True)

    return target


def read_machine_id():
    path = machine_id_file()
    if not path.is_file():
        return None
    try:
        value = path.read_text(encoding="utf-8").strip()
    except OSError:
        return None

    return value or None


def write_machine_id(machine_id):
    import tempfile

    directory = ensure_private_dir()
    handle, temp_path = tempfile.mkstemp(dir=str(directory), prefix=".machine-id-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            stream.write(machine_id)
        os.chmod(temp_path, 0o600)
        os.replace(temp_path, str(directory / "machine-id"))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return machine_id_file()


def ensure_machine_id(machine_id_factory=None):
    """Return the stable machine id, generating one if absent."""
    existing = read_machine_id()
    if existing:
        return existing
    if machine_id_factory is None:
        import uuid

        machine_id_factory = lambda: str(uuid.uuid4())  # noqa: E731
    machine_id = machine_id_factory()
    write_machine_id(machine_id)

    return machine_id
