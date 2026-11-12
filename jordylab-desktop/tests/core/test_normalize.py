from jordylab_scan.normalize import (
    extension,
    parent_directory,
    sanitize_title,
    title_from_filename,
)
from jordylab_scan.platforms import platform_for


def test_title_strips_extension_underscore_and_bracket_tags():
    assert title_from_filename("snes/Super_Mario_World_[USA]_(Rev 1).sfc") == "Super Mario World"


def test_title_strips_region_and_revision_tags():
    assert title_from_filename("psx/Final Fantasy VII (USA) (Disc 1).cue") == "Final Fantasy VII"


def test_title_collapses_whitespace_and_trims():
    assert title_from_filename("genesis/Sonic   The  Hedgehog .md") == "Sonic The Hedgehog"


def test_title_keeps_name_without_extension():
    assert title_from_filename("n64/Perfect Dark") == "Perfect Dark"


def test_extension_lowercases_and_includes_dot():
    assert extension("snes/Game.SFC") == ".sfc"


def test_extension_returns_none_without_dot():
    assert extension("snes/Game") is None


def test_extension_returns_none_for_dotfile():
    assert extension("snes/.hidden") is None


def test_parent_directory_returns_immediate_parent():
    assert parent_directory("roms/snes/Game.sfc") == "snes"


def test_parent_directory_is_none_at_root():
    assert parent_directory("Game.sfc") is None


def test_sanitize_title_removes_markup_and_controls():
    assert sanitize_title("<b>Hello</b>\x07  World") == "Hello World"


def test_sanitize_title_passes_through_none():
    assert sanitize_title(None) is None


def test_platform_for_known_and_unknown():
    assert platform_for("snes") == "SNES"
    assert platform_for("ps2") == "PlayStation 2"
    assert platform_for("atari2600") == "Atari 2600"


def test_platform_for_unknown_capitalises_first_letter():
    assert platform_for("foo") == "Foo"
    assert platform_for("FooBar") == "Foobar"
