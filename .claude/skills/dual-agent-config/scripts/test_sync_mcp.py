#!/usr/bin/env python3
"""Tests for sync_mcp.py: OAuth carry-over and per-harness callback ports.

Run from the repository root:
    python3 -m unittest discover -s .claude/skills/dual-agent-config/scripts -p 'test_*.py'
"""

import importlib.util
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
    "enabled": True,
    "oauth": {
        "clientId": "example-mcp-client",
        "callbackPort": 19876,
        "scope": "example.mcp",
    },
}


class OAuthTranslationTests(unittest.TestCase):
    def test_claude_oauth_becomes_opencode_oauth(self):
        warnings = []
        entry = sync_mcp.claude_entry_to_opencode("example", EXAMPLE_CLAUDE, warnings)
        self.assertEqual(
            entry["oauth"], {"clientId": "example-mcp-client", "callbackPort": 19877}
        )
        self.assertEqual(warnings, [])

    def test_opencode_scope_is_carried_to_opencode(self):
        warnings = []
        oauth = {"clientId": "c", "callbackPort": 1, "scope": "a.b"}
        self.assertEqual(
            sync_mcp.translate_oauth("x", oauth, "opencode", warnings), oauth
        )
        self.assertEqual(warnings, [])

    def test_scope_is_reported_not_copied_to_claude(self):
        warnings = []
        oauth = {"clientId": "c", "callbackPort": 1, "scope": "a.b"}
        result = sync_mcp.translate_oauth("x", oauth, "claude", warnings)
        self.assertEqual(result, {"clientId": "c", "callbackPort": 1})
        self.assertTrue(any("scope" in w for w in warnings))

    def test_client_secret_is_refused_in_either_direction(self):
        secret_oauth = {"clientId": "c", "clientSecret": "s3cret"}
        for target in ("opencode", "claude"):
            with self.subTest(target=target), self.assertRaises(sync_mcp.TranslationError):
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
        self.assertTrue(any("kept the existing callbackPort 19876" in w for w in warnings))

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
        self.assertEqual(merged["example"]["oauth"]["callbackPort"], 19877)
        self.assertTrue(any("new entry takes callbackPort 19877" in w for w in warnings))

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
        self.assertEqual(merged["nx-mcp"], {"type": "local", "command": ["bunx", "nx", "mcp"], "enabled": True})


if __name__ == "__main__":
    unittest.main()
