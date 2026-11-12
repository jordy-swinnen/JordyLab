import os
import unicodedata
from concurrent.futures import ThreadPoolExecutor

from jordylab_scan.manifest import (
    ScanEntry,
    canonical_json,
    canonicalize_roots,
    collect_manifest_contents,
    compute_digest,
    merge_entries,
    nfc,
    read_text,
    to_posix,
    walk_entries,
)


def _write(path, content="data"):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

    return path


def test_digest_is_stable_across_two_walks(make_tree):
    root = make_tree({"snes/Game (USA).sfc": "rom", "nes/Other.nes": "rom"})

    first = compute_digest(walk_entries(root))
    second = compute_digest(walk_entries(root))

    assert first == second
    assert first.startswith("sha256:")


def test_size_or_mtime_change_changes_digest(make_tree):
    root = make_tree({"snes/Game.sfc": "rom"})
    entry = walk_entries(root)[0]
    original = compute_digest([entry])

    changed_size = type(entry)(entry.relpath, entry.size + 1, entry.mtime_ns)
    assert compute_digest([changed_size]) != original

    changed_mtime = type(entry)(entry.relpath, entry.size, entry.mtime_ns + 1)
    assert compute_digest([changed_mtime]) != original


def test_nfd_filename_is_one_game_with_nfc_digest(make_tree):
    nfd_name = "Cafe\u0301 (USA).sfc"
    normalized = unicodedata.normalize("NFC", nfd_name)
    root = make_tree({"snes/" + nfd_name: "rom"})

    entries = walk_entries(root)
    assert [entry.relpath for entry in entries] == ["snes/" + normalized]

    nfc_entries = [type(entries[0])("snes/" + normalized, entries[0].size, entries[0].mtime_ns)]
    assert compute_digest(entries) == compute_digest(nfc_entries)


def test_manifest_contents_participate_in_digest(make_tree):
    root = make_tree({"steam/steamapps/appmanifest_220.acf": '"AppState" { "name" "x" }'})
    entries = walk_entries(root)
    empty = compute_digest(entries, {})
    with_contents = compute_digest(entries, {entries[0].relpath: "text"})

    assert empty != with_contents


def test_canonical_json_sorts_paths_and_games(make_tree):
    root = make_tree({"b.sfc": "1", "a.sfc": "2"})
    entries = walk_entries(root)
    games = [("z", "Zed", "P"), ("a", "Aye", "P")]
    document = canonical_json(entries, {}, games)

    assert '"paths":[["a.sfc"' in document
    assert document.index('"a"') < document.index('"z"')


def test_symlink_loop_does_not_hang(make_tree):
    root = make_tree({"snes/Game.sfc": "rom"})
    loop = root / "snes" / "loop"
    os.symlink(str(root), str(loop))

    with ThreadPoolExecutor(max_workers=1) as executor:
        entries = executor.submit(walk_entries, root).result(timeout=10)

    assert any(entry.relpath == "snes/Game.sfc" for entry in entries)


def test_realpath_root_dedupe(tmp_path):
    root = tmp_path / "lib"
    _write(root / "Game.sfc")
    link = tmp_path / "lib-link"
    os.symlink(str(root), str(link))

    canonical = canonicalize_roots([root, link])

    assert len(canonical) == 1


def test_walk_include_filter(make_tree):
    root = make_tree({"a/one.txt": "x", "a/two.sfc": "y"})

    entries = walk_entries(root, include=lambda relpath: relpath.endswith(".sfc"))

    assert [entry.relpath for entry in entries] == ["a/two.sfc"]


def test_read_text_returns_none_for_missing_file(tmp_path):
    assert read_text(tmp_path / "nope.txt") is None


def test_collect_manifest_contents_filters_entries(make_tree):
    root = make_tree(
        {"steam/appmanifest_1.acf": "one", "steam/appmanifest_2.acf": "two", "steam/other.txt": "x"}
    )
    entries = walk_entries(root)

    contents = collect_manifest_contents(root, entries, lambda relpath: relpath.endswith(".acf"))

    assert contents == {"steam/appmanifest_1.acf": "one", "steam/appmanifest_2.acf": "two"}


def test_merge_entries_dedupes_by_relpath():
    target = [ScanEntry("a", 1, 1)]
    merge_entries(target, [ScanEntry("a", 9, 9), ScanEntry("b", 2, 2)])

    assert [entry.relpath for entry in target] == ["a", "b"]
    assert target[0].size == 1


def test_nfc_and_posix_helpers():
    assert nfc(None) is None
    assert nfc("Cafe\u0301") == "Caf\u00e9"
    assert to_posix("a/b") == "a/b"


def test_canonicalize_roots_skips_broken(tmp_path):
    assert canonicalize_roots([tmp_path / "does-not-exist"]) != []
