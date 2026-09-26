"""Filename normalization rules.

Verbatim port of the server's ``EmuDeckLibraryParser`` extension/title rules and
``TextSanitizer.sanitizeTitle`` so that a game keeps the same identity, title,
and platform the server would have produced. Do not "improve" these rules:
identity continuity depends on them matching exactly.
"""

import re

_SCRIPT_STYLE_PATTERN = re.compile(r"<(script|style)[^>]*>.*?</\1>", re.IGNORECASE | re.DOTALL)
_MARKUP_PATTERN = re.compile(r"<[^>]*>")
# Java: [\p{Cntrl}&&[^\t]] — control characters except tab.
_CONTROL_PATTERN = re.compile(r"[\x00-\x08\x0a-\x1f\x7f]")
_WHITESPACE_PATTERN = re.compile(r"\s+")
_PAREN_GROUP_PATTERN = re.compile(r"\(.*?\)")


def extension(relpath):
    """Lower-cased extension including the dot, or ``None`` when absent."""
    slash = relpath.rfind("/")
    basename = relpath if slash < 0 else relpath[slash + 1 :]
    dot = basename.rfind(".")
    if dot <= 0:
        return None

    return basename[dot:].lower()


def parent_directory(relpath):
    """The immediate parent directory name, or ``None`` when there is none."""
    slash = relpath.rfind("/")
    if slash < 0:
        return None
    previous = relpath.rfind("/", 0, slash)
    if previous < 0:
        return relpath[:slash]

    return relpath[previous + 1 : slash]


def title_from_filename(relpath):
    """Port of ``EmuDeckLibraryParser.titleFromFilename`` (verbatim rules)."""
    slash = relpath.rfind("/")
    basename = relpath if slash < 0 else relpath[slash + 1 :]
    dot = basename.rfind(".")
    if dot > 0:
        basename = basename[:dot]
    basename = basename.replace("_", " ")
    basename = basename.replace("[", "(").replace("]", ")")
    basename = _PAREN_GROUP_PATTERN.sub("", basename)
    basename = _WHITESPACE_PATTERN.sub(" ", basename)

    return basename.strip()


def sanitize_title(raw_title):
    """Port of ``TextSanitizer.sanitizeTitle``."""
    if raw_title is None:
        return None
    cleaned = _SCRIPT_STYLE_PATTERN.sub("", raw_title)
    cleaned = _MARKUP_PATTERN.sub("", cleaned)
    cleaned = _CONTROL_PATTERN.sub("", cleaned)
    cleaned = _WHITESPACE_PATTERN.sub(" ", cleaned)

    return cleaned.strip()
