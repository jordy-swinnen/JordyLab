---
name: kaneo-design
description: Optional design step after /kaneo-refinement for tickets flagged needs-design or needs-ui-design — answer the architectural questions (module boundaries, data model, integrations, auth, infra, AI routing, testing and rollout) as short recorded decisions, and work out whether the Claude Design canvas or design system must be updated first, with a design brief and image references. Use for "design JOR-123", "what decisions do we need before speckit-plan", "does this need a Claude Design update", or when a refined ticket carries a design flag. Moves the ticket to Todo when design is settled.
---

# kaneo-design — decide the hard parts before SpecKit

Refinement says *what* and *why*. Some tickets cannot go into SpecKit yet because a decision is still open that would make the plan fall apart later: a boundary between modules, a data model, a new dependency, a screen nobody has designed. This skill settles exactly those decisions and records them in the ticket, so `/speckit-plan` starts from choices, not guesses. It is optional: tickets without a design flag skip it.

It does **not** produce a plan, a task list or code. That is `/speckit-plan` and `/speckit-tasks`. A decision here is one paragraph, not a document.

Read the `kaneo-board` skill first (columns, labels, Ready template, Definition of Ready, image rules).

## Input and entry check

A ticket key. The ticket should be in `refinement` and carry `needs-design`, `needs-ui-design`, or both. If it carries neither, say so and ask whether the owner still wants a design pass (they may have spotted something refinement missed); if not, point back to `/kaneo-refinement`. If the ticket has open questions or `[NEEDS CLARIFICATION]` markers on its stories, send it back to refinement: design built on unsettled behaviour has to be redone.

Read the whole ticket first. The stories, requirements and edge cases are your constraints; the design must satisfy every FR and scenario, and when it cannot, that is a finding to report, not something to quietly bend.

## Track A — architecture (`needs-design`)

1. **Gather the constraints before asking.** Read `AGENTS.md` and the nested `AGENTS.md` of the subproject involved, `.specify/memory/constitution.md`, the specs and code that touch the area (`specs/`, module root packages), and `docs/` for deployment. Typical constraints in this repo, verify them against the files rather than trusting this list: modular monolith with Spring Modulith (module root package is the public API, internals are never imported across modules); each module owns its Flyway schema; every AI call goes through `ResilientAiService`; Keycloak roles gate access; Angular uses signals with a signal store in the domain's `api` lib and container/presentation components; production runs Docker Compose behind Caddy; trading requires human approval for every order.
2. **List the real decisions.** Walk this checklist and keep only the items the ticket actually forces:
   - Which module owns this, and does it need a new public facade or event across a boundary?
   - Data: new tables or columns, who owns the schema, migration of existing rows, retention.
   - API: new endpoints or messages, who may call them (roles), error behaviour.
   - Integrations and dependencies: new service, library or credential; what happens when it is down or slow.
   - AI: new feature key in `ResilientAiService`, prompt location, cost and token impact, fallback.
   - Frontend: state ownership, new remote or lib, reuse of existing components.
   - Infrastructure: containers, volumes, ports, secrets, backup impact, effect on auto-deploy.
   - Security and privacy: who sees what, what is logged, what could be abused.
   - Testing: what the cheapest convincing test of each P1 scenario is, and what fixtures it needs.
   - Rollout and rollback: can it ship dark or behind a flag, and how do we undo it.
3. **Ask the owner only the real forks.** For each decision with more than one reasonable answer, ask with the structured-question tool of your harness (AskUserQuestion in Claude Code and Claude Desktop, `question` in OpenCode, else a numbered list). Give two or three options, each with its main trade-off, put your recommendation first and say why. At most four questions per round, about two rounds. Decisions with an obvious answer given the constraints are not questions: write them down as decided and move on. Where you need a fact you do not have (does the current host have room for this container?), find it or mark it as an open risk; do not invent it.
4. **Record each decision** in the ticket's Design notes section:
   ```
   ### D1 — <the decision as a short noun phrase>
   **Decision:** <what we will do>
   **Why:** <the deciding reason, tied to a story, FR or constraint>
   **Rejected:** <the main alternative and why not>
   **Consequences:** <what this makes easy, what it makes hard, any follow-up ticket>
   ```
   Add a final **Risks and unknowns** list for anything unresolved that the plan must test early. Follow-up work that is out of this ticket's scope becomes its own ticket in `ideas` (use the `kaneo-idea` approach), linked with `related`.

## Track B — UI design (`needs-ui-design`)

Claude Design holds JordyLab's design system and screen canvas. As of ticket JOR-55 they are: the redesign canvas `https://claude.ai/artifact/8TnpLPEsqby1SDLHt829tt` (screens in all six themes) and the design system `https://claude.ai/artifact/CyDU98UaWQGKji2vHSDfXN` (tokens, components, mobile rules, states, new-screen workflow). Both are private to the owner and may have been superseded; if a link does not open, ask the owner for the current one rather than guessing. The design system describes the six themes (dark: Content Led, Oxblood, Flare Orange; light: Paper, Glasshouse, Heather), mobile-first screens, and the accessibility bar of at least 4.5:1 text contrast.

1. **Classify the impact** and write it in the ticket's UI design impact section:
   - **None** — the ticket changes behaviour or data only.
   - **Within the existing design system** — screens or states already exist; only content or data changes.
   - **Needs a Claude Design update** — any of: a new screen; a changed layout or navigation; a component or pattern the design system lacks; states not yet designed (loading, empty, error, no-permission, offline); a mobile layout change.
2. **Write the design brief** when an update is needed. It is a short list the designer (Claude Design or the owner) can act on: the stories it serves (`US1`, `US2`), each screen or component with its states, what exists and can be reused, what is new, behaviour per breakpoint (360, 390, 430 px and desktop), theme concerns, and the acceptance scenarios the UI must make possible. No pixel values or colours: those come from the design system.
3. **Update the design when you can.** If your session has an Artifact or Design tool (Claude Desktop / Cowork), offer to update the existing design artifact with the new screens and states, read it first, publish to the same URL, and capture a preview image. If you only have the repo (Claude Code, OpenCode), write the brief as a ready-to-paste prompt for Claude Design and ask the owner to run it and bring back the link and a screenshot. Never claim the design is updated until a link exists.
4. **Record the references** under UI design impact → References: the artifact links, the repo path where exported images live (`docs/design/JOR-<n>/<name>.png`; creating the files in the repo is the owner's or a coding agent's step), and optionally one small thumbnail as described in `kaneo-board` ("Images in tickets": a `data:` URI, 480 px wide and 10 KB at most; external image URLs do not render on this board). Then `detach_label_from_task` the `needs-ui-design` label (find the label copy id as described in `kaneo-board`). Keep the type and app labels as they are.
5. **If the owner skips the design update** (they want SpecKit to proceed with a text brief), record "Design skipped by the owner on <date>: <reason>" under References, keep the brief, and remove the flag. It is their call; the record keeps it honest.

## Finishing

When the design flags are resolved: check the Definition of Ready yourself (design item included), check that nothing in Design notes contradicts a story or requirement, show the owner a short summary (decisions D1..Dn with one line each, UI impact and references, remaining risks) and ask for the go-ahead. On yes: `detach_label_from_task` any remaining design flag, `update_task_status` to `todo`, comment ("Design settled, DoR 12/12: <decisions in one line>"), and finish with `/speckit-specify JOR-<n>`; mention that the Design notes are inputs for `/speckit-plan`.

If a decision cannot be made now (needs a spike, a purchase, an answer from someone else), keep the ticket in `refinement`, say exactly what is blocking, and offer to create a spike ticket linked with `blocks`.

## Example Design notes entry

```
### D1 — Votes live in the gamecatalog module, one row per person per event
**Decision:** Add the voting data to the gamecatalog schema behind a new facade method; the frontend reads it through the existing gamecatalog signal store.
**Why:** FR-002 ties a vote to a catalog game, and the module already owns games and the guest role checks; a new module would need cross-module calls for every read.
**Rejected:** A standalone `events` module — right if events grow beyond voting, premature for one use today.
**Consequences:** Easy to ship as a small migration. If events later get their own features, extract then; note it in the follow-up ticket.
```
