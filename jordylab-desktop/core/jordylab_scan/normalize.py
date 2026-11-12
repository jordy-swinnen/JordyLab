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

# Scene release suffixes, e.g. "BurnOut USA MULTi5 PROPER READNFO PSP-MUPSP". Only a run of scene tags
# ending in "<platform>-<group>" at the very end is removed, so a title such as "Born in the USA" stays.
_SCENE_TAG = (
    r"(?:USA|EUR|JPN|JAP|WORLD|NTSC|PAL|MULTi\d?|PROPER|READNFO|REPACK|RERIP|FIX|"
    r"PSN|PSP|PS2|PS3|PSX|PSV|NDS|N3DS|GBA|GBC|GB|SNES|NES|N64|GC|WIIU|WII|SWITCH|"
    r"XBOX|X360|XBX|PC)"
)
_SCENE_SUFFIX_PATTERN = re.compile(
    r"\s+" + _SCENE_TAG + r"(?:\s+" + _SCENE_TAG + r")*\s*-\s*[A-Za-z0-9]+$",
    re.IGNORECASE,
)
_DEC_SUFFIX_PATTERN = re.compile(r"\s+\.?dec$", re.IGNORECASE)


def strip_scene_release_tags(raw_title):
    """Remove a scene release suffix and a decrypted-image suffix from an EmuDeck title."""
    if raw_title is None:
        return None
    cleaned = _SCENE_SUFFIX_PATTERN.sub("", raw_title)
    cleaned = _DEC_SUFFIX_PATTERN.sub("", cleaned)

    return _WHITESPACE_PATTERN.sub(" ", cleaned).strip()


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

    return strip_scene_release_tags(basename).strip()


def sanitize_title(raw_title):
    """Port of ``TextSanitizer.sanitizeTitle``."""
    if raw_title is None:
        return None
    cleaned = _SCRIPT_STYLE_PATTERN.sub("", raw_title)
    cleaned = _MARKUP_PATTERN.sub("", cleaned)
    cleaned = _CONTROL_PATTERN.sub("", cleaned)
    cleaned = _WHITESPACE_PATTERN.sub(" ", cleaned)

    return cleaned.strip()
