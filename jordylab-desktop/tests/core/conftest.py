import pytest


@pytest.fixture(autouse=True)
def isolated_dirs(tmp_path, monkeypatch):
    config_dir = tmp_path / "config"
    state_dir = tmp_path / "state"
    monkeypatch.setenv("JORDYLAB_SCAN_CONFIG_DIR", str(config_dir))
    monkeypatch.setenv("JORDYLAB_SCAN_STATE_DIR", str(state_dir))

    return {"config": config_dir, "state": state_dir}


@pytest.fixture
def make_tree(tmp_path):
    def _make(files):
        for relative, content in files.items():
            path = tmp_path / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, bytes):
                path.write_bytes(content)
            else:
                path.write_text(content, encoding="utf-8")

        return tmp_path

    return _make
