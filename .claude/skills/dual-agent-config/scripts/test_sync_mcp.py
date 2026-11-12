#!/usr/bin/env python3
"""Tests for sync_mcp.py: OAuth carry-over, per-harness callback ports and the OpenCode 2 shape.

Run from the repository root:
    python3 -m unittest discover -s .claude/skills/dual-agent-config/scripts -p 'test_*.py'
"""

import importlib.util
import json
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("sync_mcp.py")
spec = importlib.util.spec_from_file_location("sync_mcp", SCRIPT)
sync_mcp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync_mcp)

EXAMPLE_CLAUDE = {
    "type": "http",
    "url": "https://example.com/mcp",
    "oauth": {"clientId": "example-mcp-client", "callbackPort": 19877},
}
EXAMPLE_OPENCODE_EXISTING = {
    "type": "remote",
    "url": "https://example.com/mcp",
    "oauth": {
        "client_id": "example-mcp-client",
        "callback_port": 19876,
        "scope": "example.mcp",
    },
}


class OAuthTranslationTests(unittest.TestCase):
    def test_claude_oauth_becomes_opencode_oauth(self):
        warnings = []
        entry = sync_mcp.claude_entry_to_opencode("example", EXAMPLE_CLAUDE, warnings)
        self.assertEqual(
            entry["oauth"], {"client_id": "example-mcp-client", "callback_port": 19877}
        )
        self.assertEqual(warnings, [])

    def test_opencode_scope_is_carried_to_opencode(self):
        warnings = []
        oauth = {"client_id": "c", "callback_port": 1, "scope": "a.b"}
        self.assertEqual(
            sync_mcp.translate_oauth("x", oauth, "opencode", warnings), oauth
        )
        self.assertEqual(warnings, [])

    def test_scope_is_reported_not_copied_to_claude(self):
        warnings = []
        oauth = {"client_id": "c", "callback_port": 1, "scope": "a.b"}
        result = sync_mcp.translate_oauth("x", oauth, "claude", warnings)
        self.assertEqual(result, {"clientId": "c", "callbackPort": 1})
        self.assertTrue(any("scope" in w for w in warnings))

    def test_client_secret_is_refused_in_either_direction(self):
        for secret_oauth in ({"clientId": "c", "clientSecret": "s"}, {"client_id": "c", "client_secret": "s"}):
            for target in ("opencode", "claude"):
                with self.subTest(target=target, keys=sorted(secret_oauth)), self.assertRaises(sync_mcp.TranslationError):
                    sync_mcp.translate_oauth("x", secret_oauth, target, [])

    def test_non_object_oauth_is_refused(self):
        with self.assertRaises(sync_mcp.TranslationError):
            sync_mcp.translate_oauth("x", "not-an-object", "opencode", [])


class MergeTests(unittest.TestCase):
    def test_existing_callback_port_is_kept_and_scope_survives(self):
        # The example case: Claude Code uses 19877, OpenCode 19876, and OpenCode keeps its scope.
        warnings = []
        translated = {
            "example": sync_mcp.claude_entry_to_opencode("example", EXAMPLE_CLAUDE, [])
        }
        merged = sync_mcp.merge_servers(
            {"example": EXAMPLE_OPENCODE_EXISTING}, translated, warnings
        )
        self.assertEqual(merged["example"]["oauth"], EXAMPLE_OPENCODE_EXISTING["oauth"])
        self.assertTrue(any("kept the existing callback_port 19876" in w for w in warnings))

    def test_result_matches_the_existing_entry_when_nothing_changed(self):
        translated = {
            "example": sync_mcp.claude_entry_to_opencode("example", EXAMPLE_CLAUDE, [])
        }
        merged = sync_mcp.merge_servers(
            {"example": EXAMPLE_OPENCODE_EXISTING}, translated, []
        )
        self.assertEqual(merged["example"], EXAMPLE_OPENCODE_EXISTING)

    def test_new_server_takes_the_source_port_with_a_warning(self):
        warnings = []
        translated = {
            "example": sync_mcp.claude_entry_to_opencode("example", EXAMPLE_CLAUDE, [])
        }
        merged = sync_mcp.merge_servers({}, translated, warnings)
        self.assertEqual(merged["example"]["oauth"]["callback_port"], 19877)
        self.assertTrue(any("new entry takes callback_port 19877" in w for w in warnings))

    def test_existing_oauth_is_kept_when_the_source_has_none(self):
        warnings = []
        source = {"type": "http", "url": "https://example.com/mcp"}
        translated = {"example": sync_mcp.claude_entry_to_opencode("example", source, [])}
        merged = sync_mcp.merge_servers(
            {"example": EXAMPLE_OPENCODE_EXISTING}, translated, warnings
        )
        self.assertEqual(merged["example"]["oauth"], EXAMPLE_OPENCODE_EXISTING["oauth"])
        self.assertTrue(any("existing one was kept" in w for w in warnings))

    def test_servers_without_oauth_are_unchanged(self):
        source = {"type": "stdio", "command": "bunx", "args": ["nx", "mcp"]}
        translated = {"nx-mcp": sync_mcp.claude_entry_to_opencode("nx-mcp", source, [])}
        merged = sync_mcp.merge_servers({}, translated, [])
        self.assertEqual(merged["nx-mcp"], {"type": "local", "command": ["bunx", "nx", "mcp"]})



class OpenCodeV2Tests(unittest.TestCase):
    def test_v1_and_v2_servers_are_both_read(self):
        doc = {"mcp": {"timeout": {"catalog": 1}, "old": {"type": "local", "command": ["a"]},
                       "servers": {"new": {"type": "remote", "url": "https://x"}}}}
        self.assertEqual(sorted(sync_mcp.opencode_servers(doc)), ["new", "old"])

    def test_to_opencode_writes_under_servers_and_converts_v1_entries(self):
        warnings = []
        target = {"mcp": {"timeout": {"catalog": 5}, "old": {"type": "local", "command": ["a"], "enabled": False, "timeout": 3000}}}
        translated = {"new": {"type": "remote", "url": "https://x"}}
        doc, _ = sync_mcp.write_target("to-opencode", target, translated, warnings)
        self.assertEqual(doc["mcp"], {
            "timeout": {"catalog": 5},
            "servers": {
                "old": {"type": "local", "command": ["a"], "disabled": True, "timeout": {"catalog": 3000, "execution": 3000}},
                "new": {"type": "remote", "url": "https://x"},
            },
        })
        self.assertTrue(any("OpenCode 2 shape" in w for w in warnings))

    def test_to_claude_reads_snake_case_oauth_and_warns_on_disabled(self):
        warnings = []
        entry = {"type": "remote", "url": "https://x", "disabled": True,
                 "oauth": {"client_id": "c", "callback_port": 7}}
        result = sync_mcp.opencode_entry_to_claude("x", entry, warnings)
        self.assertEqual(result["oauth"], {"clientId": "c", "callbackPort": 7})
        self.assertTrue(any("disabled" in w for w in warnings))

    def test_oauth_false_is_not_copied_to_claude(self):
        warnings = []
        result = sync_mcp.opencode_entry_to_claude("x", {"type": "remote", "url": "https://x", "oauth": False}, warnings)
        self.assertNotIn("oauth", result)
        self.assertTrue(any("oauth: false" in w for w in warnings))

    def test_jsonc_comments_and_trailing_commas_are_stripped_but_strings_kept(self):
        text = '{\n  // note\n  "url": "https://a//b", /* x */ "s": "a,}",\n  "l": [1, 2,],\n}'
        self.assertEqual(json.loads(sync_mcp.strip_jsonc(text)), {"url": "https://a//b", "s": "a,}", "l": [1, 2]})


if __name__ == "__main__":
    unittest.main()
