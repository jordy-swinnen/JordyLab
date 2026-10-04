# Contract: AI rules file and loading

| Item | Contract |
|------|----------|
| Rules file | `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`, about 25 lines, only high-confidence findings from the report that fit this repo |
| Claude Code import | `.../shared/ai/CLAUDE.md` containing exactly `@AGENTS.md` |
| Backend pointer | one line in the Spring AI section of `jordylab-be/AGENTS.md` pointing at the rules file and `docs/research/spring-ai-architecture.md` |
| Root pointer | one row in the Reference Docs table of the root `AGENTS.md` for the reference doc; the doc is not imported anywhere |
| Size rule | no always-loaded file grows by more than a pointer line (SC-009) |
| Checklist | the same checklist text in `.claude/skills/ai-endpoint/SKILL.md`, `.claude/agents/code-reviewer.md` and `.opencode/agents/code-reviewer.md`; a diff of the checklist block between the two reviewer copies must be empty |
| Excluded | anything the report marks unverified (stays in the reference doc) |

Verification (FR-032): `/context` in a Claude Code session in the package shows the rules; a fresh OpenCode session
there shows them. Checks that cannot run in this environment are reported and handed over.
