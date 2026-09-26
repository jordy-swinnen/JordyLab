import json
import os
import stat

import pytest

from jordylab_scan import config


def test_configured_roots_reads_library_type(tmp_path):
    path = tmp_path / "config.json"
    path.write_text(json.dumps({"roots": {"EMUDECK": ["/roms"]}}), encoding="utf-8")

    assert config.configured_roots("EMUDECK", path=path) == ["/roms"]
    assert config.configured_roots("STEAM", path=path) == []


def test_configured_roots_handles_bad_json(tmp_path):
    path = tmp_path / "config.json"
    path.write_text("not json", encoding="utf-8")

    assert config.configured_roots("STEAM", path=path) == []


def test_resolve_service_precedence():
    constants = {"KEYCLOAK_URL": "http://kc", "REALM": "realm", "LIBRARY_TYPE": "STEAM"}
    data = {"service": {"keycloak_url": "http://other", "client_id": "from-config"}}
    env = {"JORDYLAB_SCAN_CLIENT_ID": "from-env", "JORDYLAB_SCAN_BACKEND_URL": "http://env"}

    resolved = config.resolve_service("STEAM", constants=constants, config_data=data, env=env)

    assert resolved.keycloak_url == "http://kc"
    assert resolved.realm == "realm"
    assert resolved.client_id == "from-config"
    assert resolved.backend_url == "http://env"
    assert resolved.scan_endpoint == config.DEFAULT_SCAN_ENDPOINT


def test_resolve_service_treats_placeholders_as_absent():
    resolved = config.resolve_service(
        "EMUDECK", constants={"KEYCLOAK_URL": "${KEYCLOAK_URL}"}, config_data={}, env={}
    )

    assert resolved.keycloak_url == config.DEFAULT_KEYCLOAK_URL


def test_check_endpoint_derived_from_scan_endpoint():
    cfg = config.resolve_service("STEAM", config_data={}, env={})

    assert cfg.check_endpoint() == "/api/gamecatalog/ingest/check"


def test_normalize_library_type_is_case_insensitive():
    assert config.normalize_library_type("steam") == "STEAM"


def test_normalize_library_type_rejects_unknown():
    with pytest.raises(ValueError):
        config.normalize_library_type("switch")


def test_ensure_machine_id_generates_stable_private_file():
    first = config.ensure_machine_id(machine_id_factory=lambda: "machine-uuid")
    second = config.ensure_machine_id(machine_id_factory=lambda: "different")

    assert first == "machine-uuid"
    assert second == "machine-uuid"
    mode = stat.S_IMODE(os.stat(str(config.machine_id_file())).st_mode)
    assert mode == 0o600
