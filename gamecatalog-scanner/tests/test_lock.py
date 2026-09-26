import pytest

from jordylab_scan.lock import LockTimeout, file_lock


def test_file_lock_roundtrip(tmp_path):
    path = tmp_path / "nested" / "scan.lock"

    with file_lock(path) as handle:
        assert handle is not None

    assert path.is_file()


def test_file_lock_contention_times_out(tmp_path):
    path = tmp_path / "scan.lock"

    with file_lock(path):
        with pytest.raises(LockTimeout):
            with file_lock(path, timeout=0.05, poll_interval=0.01):
                pass  # pragma: no cover
