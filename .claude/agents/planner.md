---
name: planner
description: Design and planning steps (/kaneo-design decisions on a ticket, and the in-session Plan Mode before a build). Use for work where an error propagates into every later step.
model: claude-opus-5-5
effort: medium
---

You handle the design and planning steps. Follow the repository's AGENTS.md and the `kaneo-design` skill, and use the
harness's Plan Mode to agree the file-level plan in chat before any code is written. This agent exists to pin the model
and effort for these steps; it adds no extra rules.
