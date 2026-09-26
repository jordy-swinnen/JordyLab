"""Cross-process advisory lock.

``fcntl.flock`` on POSIX, ``msvcrt.locking`` on Windows. Used around token
refresh and around the whole scan run so two overlapping runs cannot corrupt
the cached session or race each other.
"""

import errno
import os
import time
from contextlib import contextmanager

try:  # pragma: no cover - platform specific
    import fcntl
except ImportError:  # pragma: no cover - Windows
    fcntl = None

try:  # pragma: no cover - platform specific
    import msvcrt
except ImportError:  # pragma: no cover - POSIX
    msvcrt = None


class LockTimeout(Exception):
    """Raised when the lock could not be acquired within the timeout."""


def _acquire_posix(handle):
    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)


def _release_posix(handle):
    fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def _acquire_windows(handle):  # pragma: no cover - Windows only
    handle.seek(0)
    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)


def _release_windows(handle):  # pragma: no cover - Windows only
    handle.seek(0)
    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)


def _try_acquire(handle):
    if fcntl is not None:
        _acquire_posix(handle)
    elif msvcrt is not None:  # pragma: no cover - Windows only
        _acquire_windows(handle)
    else:  # pragma: no cover - no locking primitive
        raise LockTimeout("no file-locking primitive available on this platform")


def _release(handle):
    if fcntl is not None:
        _release_posix(handle)
    elif msvcrt is not None:  # pragma: no cover - Windows only
        _release_windows(handle)


@contextmanager
def file_lock(path, timeout=0.0, poll_interval=0.1):
    """Hold an exclusive lock on ``path`` for the duration of the block."""
    directory = os.path.dirname(str(path))
    if directory:
        os.makedirs(directory, exist_ok=True)
    handle = open(path, "a+")
    deadline = time.monotonic() + max(0.0, timeout)
    try:
        while True:
            try:
                _try_acquire(handle)
                break
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN):
                    raise
                if time.monotonic() >= deadline:
                    raise LockTimeout(f"could not acquire lock on {path}") from error
                time.sleep(poll_interval)
        yield handle
    finally:
        try:
            _release(handle)
        except OSError:
            pass
        handle.close()
