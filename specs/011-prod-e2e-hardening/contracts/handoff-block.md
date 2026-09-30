# Contract: HANDOFF Block

Sent to Jordy in chat, batched (several per message), and logged in the test plan's Handoff log.

```markdown
#### HANDOFF-<nn>: <goal in one line>
- Machine: MacBook | JordyBox (CachyOS, Fish) | Android phone | browser
- Target env: local | prod
- Why you: <what the agent cannot do itself>
- Steps (Fish-compatible, one command per line, no heredocs):
  1. `<command>`
- You should see: <expected output / screen>
- Send back: <output excerpt | screenshot | "done"> — never paste tokens or passwords
```

Rules:
- Commands use `set -x VAR value` (not `export`), `; and` / `; or` (not `&&` / `||`) where chaining is needed on
  Fish machines; macOS commands may be zsh but say so.
- Everything is prepared first: download URL, env, expected source name `(machineId, libraryType)`.
- Target ≤ 10 minutes of Jordy's hands-on time (SC-009).
- After "done", the agent verifies via API/UI/logs before marking `verified`.
