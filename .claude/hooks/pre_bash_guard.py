#!/usr/bin/env python3
"""
PreToolUse guard for Bash commands. Replaces an earlier regex/glob-based
implementation with a real (bounded) shell tokenizer, so destructive-command
detection compares actual arguments rather than pattern-matching raw text.

That earlier approach went through eight rounds of fixes chasing edge cases
that are inherent to text-heuristic matching against a language (shell
syntax) with real recursive structure: word-boundary false positives,
separators/command-names embedded inside quoted strings, combined short
flags, multi-line heredocs, command substitution. A tokenizer closes those
as a class rather than one repro at a time.

Scanner handles: single/double quotes (correct backslash semantics per
context), heredocs (<<WORD / <<-WORD; body is opaque unless the delimiter is
unquoted, in which case its embedded substitutions are still scanned since
they're genuinely executed), here-strings (<<<), command substitution
($(...) and `...`, recursed into and checked as their own commands), and
control operators (; && || | &) as command boundaries.

Known, accepted residual gaps (rare in agent-issued commands, and this
scanner's job is finding real command boundaries/arguments, not full shell
semantics): process substitution <(...)/>(...), arithmetic $(( )), ANSI-C
$'...' quoting, brace expansion, extended globs, and variable expansion
(so a literal token like "$HOME" is matched as text, not its expanded
value -- same limitation the previous implementation had).
"""
from __future__ import annotations

import json
import re
import sys

# ---------------------------------------------------------------------------
# Tokenizer
# ---------------------------------------------------------------------------


class Scanner:
    def __init__(self, text: str):
        self.text = text
        self.n = len(text)

    def scan(self) -> tuple[list[list[str]], list[list[str]]]:
        """Returns (commands, substitution_commands)."""
        i = 0
        n = self.n
        text = self.text
        commands: list[list[str]] = []
        cur_tokens: list[str] = []
        cur_word: list[str] | None = None
        quote: str | None = None
        pending_heredocs: list[tuple[str, bool, bool]] = []
        sub_commands: list[list[str]] = []

        def end_word():
            nonlocal cur_word
            if cur_word is not None:
                cur_tokens.append("".join(cur_word))
                cur_word = None

        def end_command():
            nonlocal cur_tokens
            end_word()
            if cur_tokens:
                commands.append(cur_tokens)
            cur_tokens = []

        def start_word():
            nonlocal cur_word
            if cur_word is None:
                cur_word = []

        def extract_balanced(open_i: int, open_c: str, close_c: str) -> tuple[str, int]:
            depth = 1
            j = open_i
            q: str | None = None
            start = open_i
            while j < n:
                cj = text[j]
                if q == "'":
                    if cj == "'":
                        q = None
                elif q == '"':
                    if cj == '"':
                        q = None
                    elif cj == "\\" and j + 1 < n:
                        j += 1
                elif cj == "'":
                    q = "'"
                elif cj == '"':
                    q = '"'
                elif cj == "\\" and j + 1 < n:
                    j += 1
                elif cj == open_c:
                    depth += 1
                elif cj == close_c:
                    depth -= 1
                    if depth == 0:
                        return text[start:j], j + 1
                j += 1
            return text[start:j], j

        def extract_balanced_backtick(open_i: int) -> tuple[str, int]:
            j = open_i
            start = open_i
            while j < n:
                cj = text[j]
                if cj == "\\" and j + 1 < n:
                    j += 2
                    continue
                if cj == "`":
                    return text[start:j], j + 1
                j += 1
            return text[start:j], j

        while i < n:
            c = text[i]

            if quote == "'":
                if c == "'":
                    quote = None
                    i += 1
                else:
                    start_word()
                    cur_word.append(c)
                    i += 1
                continue

            if quote == '"':
                if c == '"':
                    quote = None
                    i += 1
                elif c == "\\" and i + 1 < n and text[i + 1] in ('"', "\\", "$", "`", "\n"):
                    start_word()
                    if text[i + 1] != "\n":
                        cur_word.append(text[i + 1])
                    i += 2
                elif c == "$" and text[i + 1 : i + 2] == "(":
                    inner, new_i = extract_balanced(i + 2, "(", ")")
                    sc, ssc = Scanner(inner).scan()
                    sub_commands.extend(sc)
                    sub_commands.extend(ssc)
                    start_word()
                    cur_word.append("$(...)")
                    i = new_i
                elif c == "`":
                    inner, new_i = extract_balanced_backtick(i + 1)
                    sc, ssc = Scanner(inner).scan()
                    sub_commands.extend(sc)
                    sub_commands.extend(ssc)
                    start_word()
                    cur_word.append("`...`")
                    i = new_i
                else:
                    start_word()
                    cur_word.append(c)
                    i += 1
                continue

            # unquoted context
            if c == "'":
                quote = "'"
                start_word()
                i += 1
                continue
            if c == '"':
                quote = '"'
                start_word()
                i += 1
                continue
            if c == "\\" and i + 1 < n:
                start_word()
                if text[i + 1] != "\n":
                    cur_word.append(text[i + 1])
                i += 2
                continue
            if c == "$" and text[i + 1 : i + 2] == "(":
                inner, new_i = extract_balanced(i + 2, "(", ")")
                sc, ssc = Scanner(inner).scan()
                sub_commands.extend(sc)
                sub_commands.extend(ssc)
                start_word()
                cur_word.append("$(...)")
                i = new_i
                continue
            if c == "`":
                inner, new_i = extract_balanced_backtick(i + 1)
                sc, ssc = Scanner(inner).scan()
                sub_commands.extend(sc)
                sub_commands.extend(ssc)
                start_word()
                cur_word.append("`...`")
                i = new_i
                continue

            if c in (" ", "\t"):
                end_word()
                i += 1
                continue

            if text[i : i + 2] == "<<":
                j = i + 2
                strip_tabs = False
                if j < n and text[j] == "-":
                    strip_tabs = True
                    j += 1
                if j < n and text[j] == "<":
                    end_word()
                    cur_tokens.append("<<<")
                    i = j + 1
                    continue
                while j < n and text[j] in (" ", "\t"):
                    j += 1
                delim_quoted = False
                delim_chars: list[str] = []
                if j < n and text[j] in ("'", '"'):
                    q = text[j]
                    delim_quoted = True
                    j += 1
                    while j < n and text[j] != q:
                        delim_chars.append(text[j])
                        j += 1
                    j += 1
                else:
                    while j < n and text[j] not in (" ", "\t", "\n", ";", "&", "|", "<", ">"):
                        delim_chars.append(text[j])
                        j += 1
                end_word()
                cur_tokens.append("<<HEREDOC")
                pending_heredocs.append(("".join(delim_chars), strip_tabs, delim_quoted))
                i = j
                continue

            if text[i : i + 2] in ("&&", "||"):
                end_command()
                i += 2
                continue

            if c in (";", "|", "&"):
                end_command()
                i += 1
                continue

            if c == "\n":
                end_word()
                if pending_heredocs:
                    i += 1
                    for delim, strip_tabs, delim_quoted in pending_heredocs:
                        body_lines: list[str] = []
                        while i <= n:
                            eol = text.find("\n", i)
                            if eol == -1:
                                line = text[i:]
                                i = n
                            else:
                                line = text[i:eol]
                                i = eol + 1
                            check_line = line.lstrip("\t") if strip_tabs else line
                            if check_line == delim:
                                break
                            body_lines.append(line)
                            if eol == -1:
                                break
                        if not delim_quoted:
                            sc, ssc = Scanner("\n".join(body_lines)).scan()
                            sub_commands.extend(sc)
                            sub_commands.extend(ssc)
                    pending_heredocs = []
                else:
                    i += 1
                continue

            start_word()
            cur_word.append(c)
            i += 1

        end_command()
        return commands, sub_commands


def all_commands(text: str) -> list[list[str]]:
    """Every simple command in the string, including ones found inside
    command substitutions (since those genuinely execute)."""
    commands, sub_commands = Scanner(text).scan()
    return commands + sub_commands


# ---------------------------------------------------------------------------
# Destructive-command checks
# ---------------------------------------------------------------------------

DANGEROUS_TARGETS = {"/", "~", ".", "..", "*", "./", "../"}
DANGEROUS_PREFIXES = ("~/", "$HOME", "/home", "/etc", "/usr", "/var", "/bin", "/boot", "/opt", "/root")

VAR_ASSIGN_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
DDL_RE = re.compile(r"\b(DROP\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE)\b", re.IGNORECASE)


def is_dangerous_target(token: str) -> bool:
    if token in DANGEROUS_TARGETS:
        return True
    return token.startswith(DANGEROUS_PREFIXES)


def is_short_opt_cluster(token: str) -> bool:
    return token.startswith("-") and not token.startswith("--") and len(token) > 1 and token[1:].isalpha()


def token_has_recursive(token: str) -> bool:
    if token == "--recursive":
        return True
    return is_short_opt_cluster(token) and any(ch in "rR" for ch in token[1:])


def token_has_force(token: str) -> bool:
    if token == "--force":
        return True
    return is_short_opt_cluster(token) and "f" in token[1:]


def resolve_program(tokens: list[str]) -> tuple[str | None, list[str]]:
    """Skips env-var assignments and a bare sudo/exec/command wrapper to
    find the actual invoked program."""
    i = 0
    n = len(tokens)
    while i < n and VAR_ASSIGN_RE.match(tokens[i]):
        i += 1
    while i < n and tokens[i] in ("sudo", "exec", "command"):
        i += 1
    if i >= n:
        return None, []
    return tokens[i], tokens[i + 1 :]


def check_ddl(tokens: list[str]) -> str | None:
    # DDL keywords are multiple words (e.g. "DROP TABLE"); when unquoted
    # these arrive as separate tokens, so join before matching rather than
    # checking each token in isolation.
    if DDL_RE.search(" ".join(tokens)):
        return "Direct DDL (DROP/TRUNCATE) is not allowed. Use Flyway migrations instead."
    return None


def check_command(tokens: list[str]) -> str | None:
    if not tokens:
        return None

    ddl = check_ddl(tokens)
    if ddl:
        return ddl

    prog, rest = resolve_program(tokens)
    if prog is None:
        return None

    if prog == "rm":
        if any(t == "--no-preserve-root" for t in rest):
            return "rm --no-preserve-root is not allowed."
        has_recursive = any(token_has_recursive(t) for t in rest)
        dangerous_target = any(is_dangerous_target(t) for t in rest if not t.startswith("-"))
        if has_recursive and dangerous_target:
            return "Destructive recursive rm against a root/home/system path is not allowed."
        return None

    if prog == "git" and rest:
        subcmd, args = rest[0], rest[1:]
        if subcmd == "reset" and "--hard" in args:
            return "git reset --hard is not allowed. Use git stash or git checkout for specific files."
        if subcmd == "clean" and any(token_has_force(t) for t in args):
            return (
                "git clean -f permanently deletes untracked files and is not allowed. "
                "Use git clean -n to preview, or -i for interactive."
            )
        if subcmd == "push" and any(token_has_force(t) for t in args):
            return "git push --force is not allowed. Use --force-with-lease if you must force push."
        return None

    if prog == "docker-compose":
        if "down" in rest and any(t in ("-v", "--volumes") for t in rest):
            return "docker compose down -v destroys volumes. Use docker compose down without -v."
        return None

    if prog == "docker" and rest and rest[0] == "compose":
        sub = rest[1:]
        if "down" in sub and any(t in ("-v", "--volumes") for t in sub):
            return "docker compose down -v destroys volumes. Use docker compose down without -v."
        return None

    return None


def check(command: str) -> str | None:
    for tokens in all_commands(command):
        msg = check_command(tokens)
        if msg:
            return msg
    return None


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0
    command = data.get("tool_input", {}).get("command") or ""
    if not command:
        return 0
    msg = check(command)
    if msg:
        print(f"BLOCKED: {msg}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
