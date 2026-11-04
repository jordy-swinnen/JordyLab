import json
import os
import stat

import pytest

from jordylab_scan import auth
from jordylab_scan.api import ReauthRequired


def test_get_token_prefers_the_cache(tmp_path, monkeypatch):
    token_file = tmp_path / "token.json"
    auth.save_token("jlmt_cached", path=token_file)

    monkeypatch.setenv("JORDYLAB_SCAN_TOKEN", "jlmt_from_env")

    assert auth.get_token(path=token_file) == "jlmt_cached"


def test_get_token_caches_the_environment_token(tmp_path, monkeypatch):
    token_file = tmp_path / "token.json"
    monkeypatch.setenv("JORDYLAB_SCAN_TOKEN", " jlmt_from_env ")

    assert auth.get_token(path=token_file) == "jlmt_from_env"
    assert auth.load_token(path=token_file) == {"token": "jlmt_from_env"}
    assert stat.S_IMODE(os.stat(str(token_file)).st_mode) == 0o600


def test_get_token_without_any_token_asks_for_a_login(tmp_path, monkeypatch):
    monkeypatch.delenv("JORDYLAB_SCAN_TOKEN", raising=False)

    with pytest.raises(ReauthRequired):
        auth.get_token(path=tmp_path / "missing.json")


def test_prompt_login_rejects_foreign_tokens(tmp_path):
    with pytest.raises(ReauthRequired):
        auth.prompt_login(path=tmp_path / "token.json", stream=open(os.devnull, "w"),
                          prompt=lambda _prompt: "not-a-token")


def test_prompt_login_saves_a_machine_token(tmp_path):
    token_file = tmp_path / "token.json"
    auth.prompt_login(path=token_file, stream=open(os.devnull, "w"),
                      prompt=lambda _prompt: "jlmt_pasted")

    assert auth.load_token(path=token_file) == {"token": "jlmt_pasted"}


def test_clear_token_removes_the_cache(tmp_path):
    token_file = tmp_path / "token.json"
    auth.save_token("jlmt_cached", path=token_file)

    auth.clear_token(path=token_file)

    assert auth.load_token(path=token_file) is None
    # Clearing twice is not an error.
    auth.clear_token(path=token_file)


def test_corrupt_cache_is_treated_as_absent(tmp_path):
    token_file = tmp_path / "token.json"
    token_file.write_text("not json", encoding="utf-8")

    assert auth.load_token(path=token_file) is None


def test_save_token_is_atomic_and_owner_only(tmp_path):
    token_file = tmp_path / "token.json"
    auth.save_token("jlmt_one", path=token_file)
    auth.save_token("jlmt_two", path=token_file)

    assert json.loads(token_file.read_text(encoding="utf-8")) == {"token": "jlmt_two"}
    assert stat.S_IMODE(os.stat(str(token_file)).st_mode) == 0o600
