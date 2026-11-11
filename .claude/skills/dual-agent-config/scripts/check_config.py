#!/usr/bin/env python3
"""Check that a Claude Code + OpenCode repository has one rulebook and no drifting config.

    python3 .claude/skills/dual-agent-config/scripts/check_config.py [--strict-portable] [--root PATH]

It checks files only, never a running agent. Exit 1 and name what to fix when a
problem would make one tool load different rules from the other.
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sync_mcp import TranslationError, load_json, opencode_servers  # noqa: E402

PORTABLE = {"name", "description", "license", "compatibility", "metadata", "allowed-tools"}
OPENCODE_CONFIGS = ("opencode.json", "opencode.jsonc", ".opencode/opencode.json", ".opencode/opencode.jsonc")
# OpenCode 1 top-level keys that OpenCode 2 still reads, and their native names.
V1_KEYS = {"agent": "agents", "mode": "agents", "permission": "permissions", "command": "commands",
           "provider": "providers", "reference": "references", "snapshot": "snapshots", "attachment": "media"}
# An agent file with any of these is read as OpenCode 2, and these V1 fields in it are ignored.
V2_AGENT_MARKERS = {"permissions", "request", "system", "disabled"}
V1_AGENT_FIELDS = {"name", "tools", "permission", "temperature", "top_p", "prompt", "disable", "maxSteps", "variant"}


def find_root(start: Path) -> Path:
    for candidate in (start, *start.parents):
        if (candidate / ".git").exists() or (candidate / "AGENTS.md").exists():
            return candidate
    return start


def frontmatter_keys(path: Path):
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        return None
    keys = []
    for line in lines[1:]:
        if line.strip() == "---":
            return keys
        match = re.match(r"^([A-Za-z][\w-]*):", line)
        if match:
            keys.append(match.group(1))
    return None


def apm_targets(path: Path):
    """Return the `targets:` (or `target:`) list of an apm.yml, without a YAML parser."""
    targets = []
    inside = False
    for line in path.read_text(encoding="utf-8").splitlines():
        top = re.match(r"^(targets?):(.*)$", line)
        if top:
            rest = top.group(2).strip()
            inside = not rest
            if rest.startswith("["):
                targets += [item.strip().strip("'\"") for item in rest.strip("[]").split(",") if item.strip()]
            elif rest:
                targets.append(rest.strip("'\""))
            continue
        if inside:
            item = re.match(r"^\s+-\s*(\S+)", line)
            if item:
                targets.append(item.group(1).strip("'\""))
            elif line.strip() and not line[:1].isspace():
                inside = False
    return targets


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, help="repository root; defaults to the nearest .git or AGENTS.md")
    parser.add_argument(
        "--strict-portable",
        action="store_true",
        help="fail on skill frontmatter fields outside the portable Agent Skills set",
    )
    args = parser.parse_args()

    root = (args.root or find_root(Path(__file__).resolve().parent)).resolve()
    problems = []
    warnings = []

    # 1. One source of rules.
    agents = root / "AGENTS.md"
    claude = root / "CLAUDE.md"
    if not agents.exists():
        problems.append("AGENTS.md is missing: Claude Code reads CLAUDE.md only, and OpenCode would have no rules.")
    if not claude.exists() or claude.read_text(encoding="utf-8").strip() != "@AGENTS.md":
        problems.append("CLAUDE.md must contain exactly one line, @AGENTS.md. OpenCode 2 never reads CLAUDE.md, so rules there are lost.")

    # 2. Skills: one copy, directly under .claude/skills (both tools read it there).
    for duplicate_root in (".opencode/skills", ".agents/skills"):
        if (root / duplicate_root).exists():
            problems.append(f"{duplicate_root} exists: skills live only in .claude/skills (both tools read it).")

    # A package manager must not deploy a second target: APM's opencode target uses .agents/skills.
    apm_manifest = root / "apm.yml"
    if apm_manifest.exists():
        targets = apm_targets(apm_manifest)
        if not targets:
            problems.append(
                "apm.yml declares no targets:: auto-detection picks up .opencode/ and deploys skills to "
                ".agents/skills. Declare targets: [claude]."
            )
        else:
            extra = sorted(target for target in targets if target != "claude")
            if extra:
                problems.append(
                    f"apm.yml targets {extra}: only `claude` deploys skills to .claude/skills, so every other "
                    "target adds a second copy. Keep targets: [claude]."
                )

    skill_roots = [folder for folder in (root / ".claude" / "skills", root / "skills") if folder.is_dir()]
    seen = {}
    for skill_root in skill_roots:
        for nested in sorted(skill_root.glob("*/*/SKILL.md")):
            problems.append(
                f"{nested.relative_to(root)}: nested skill copy; skills belong directly in .claude/skills/<name>/."
            )
        for skill in sorted(skill_root.glob("*/SKILL.md")):
            folder = skill.parent.name
            if folder in seen:
                warnings.append(f"{skill.relative_to(root)}: duplicate skill id '{folder}' also at {seen[folder]}.")
            seen[folder] = skill.relative_to(root)

            keys = frontmatter_keys(skill)
            if keys is None:
                problems.append(f"{skill.relative_to(root)}: no frontmatter.")
                continue
            extra = sorted(set(keys) - PORTABLE)
            if extra:
                message = f"{skill.relative_to(root)}: non-portable frontmatter {extra}."
                if args.strict_portable:
                    problems.append(message + " Remove them or run without --strict-portable.")
                else:
                    warnings.append(message + " OpenCode ignores them; claude.ai packaging may reject them.")

            text = skill.read_text(encoding="utf-8")
            name = re.search(r"^name:\s*(\S+)", text, re.M)
            if not name or name.group(1) != folder:
                problems.append(f"{skill.relative_to(root)}: name must equal the folder name '{folder}'.")

            # "references/x.md" is relative to this skill; "<other-skill>/references/x.md" points into another skill.
            for owner, filename in set(re.findall(r"(?:([\w-]+)/)?references/([\w.-]+\.md)", text)):
                target = (skill_root / owner if owner else skill.parent) / "references" / filename
                if not target.exists():
                    problems.append(
                        f"{skill.relative_to(root)}: mentions {(owner + '/') if owner else ''}references/{filename}, which does not exist."
                    )

    # 4. Claude-only scoped rules must use the key Claude Code recognises.
    rules_dir = root / ".claude" / "rules"
    if rules_dir.is_dir():
        for rule in rules_dir.glob("**/*.md"):
            keys = frontmatter_keys(rule) or []
            bad = sorted(set(keys) & {"match", "applyTo", "glob", "globs"})
            if bad:
                problems.append(f"{rule.relative_to(root)}: {bad} is not recognised, use paths:. The rule never loads.")

    # 5. OpenCode config: native OpenCode 2 keys, and MCP servers in both tools or neither.
    mcp_opencode = set()
    for name in OPENCODE_CONFIGS:
        path = root / name
        if not path.exists():
            continue
        try:
            doc = load_json(path)
        except TranslationError as exc:
            problems.append(str(exc))
            continue
        mcp_opencode |= set(opencode_servers(doc))
        if "plugin" in doc:
            warnings.append(f"{name}: `plugin` is the OpenCode 1 key. Use `plugins`; OpenCode 1 plugin code does not run in OpenCode 2.")
        for key in sorted(set(doc) & set(V1_KEYS)):
            warnings.append(f"{name}: `{key}` is OpenCode 1 syntax (still read). The native OpenCode 2 key is `{V1_KEYS[key]}`.")
        if isinstance(doc.get("mcp"), dict) and set(doc["mcp"]) - {"servers", "timeout"}:
            warnings.append(f"{name}: MCP servers sit directly under `mcp` (OpenCode 1). OpenCode 2 puts them under `mcp.servers`.")

    mcp_claude = set()
    if (root / ".mcp.json").exists():
        mcp_claude = set(json.loads((root / ".mcp.json").read_text(encoding="utf-8")).get("mcpServers", {}))
    if mcp_claude != mcp_opencode:
        problems.append(
            f"MCP servers differ: .mcp.json has {sorted(mcp_claude)}, OpenCode has {sorted(mcp_opencode)}. Translate them with dual-agent-config/scripts/sync_mcp.py."
        )

    # 6. Subagents: same set in both tools, and OpenCode files that OpenCode 2 reads the way they were meant.
    claude_agents, opencode_agents = set(), set()
    for agent in sorted((root / ".claude" / "agents").glob("**/*.md")):
        name = re.search(r"^name:\s*(\S+)", agent.read_text(encoding="utf-8"), re.M)
        claude_agents.add(name.group(1) if name else agent.stem)
    opencode_dir = root / ".opencode" / "agents"
    for agent in sorted(opencode_dir.glob("**/*.md")):
        rel = agent.relative_to(root)
        opencode_agents.add(agent.relative_to(opencode_dir).with_suffix("").as_posix())
        keys = set(frontmatter_keys(agent) or [])
        if "mode" not in keys:
            warnings.append(f"{rel}: no `mode`. OpenCode 2 makes it a primary agent, so it is never offered as a subagent. Add mode: subagent if that is the intent.")
        mixed = sorted(keys & V1_AGENT_FIELDS)
        if keys & V2_AGENT_MARKERS and mixed:
            problems.append(f"{rel}: read as OpenCode 2 (it has {sorted(keys & V2_AGENT_MARKERS)}), so the OpenCode 1 fields {mixed} are ignored. Convert the whole frontmatter.")
    if claude_agents and opencode_agents and claude_agents != opencode_agents:
        warnings.append(
            f"Subagents differ: .claude/agents has {sorted(claude_agents)}, .opencode/agents has {sorted(opencode_agents)}."
        )

    for warning in warnings:
        print(f"warning: {warning}")
    if problems:
        print("Agent config problems:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(f"Agent config ok: one rules file, skills checked, MCP servers in sync. Root: {root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
