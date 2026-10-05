# Feature Specification: AI Calls Outside Database Transactions

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `gamecatalog` (enrichment, scan, Steam sync), `fna` (briefing)
**Origin**: gap analysis `docs/research/spring-ai-gap-analysis.md`

---

## Overview

Model calls run inside open database transactions today, so a connection is held for up to 120 s per call (twice with the fallback), a scan upload waits on enrichment, and one late failure rolls back unrelated work.

---

## User Scenarios & Testing

### User Story: Scans and syncs finish without waiting on the model (High)

As the owner, I want a scan upload and a Steam sync to complete and commit their catalog changes before any model call starts, so that uploads do not wait on AI and a model failure cannot undo a scan.

**Independent Test**: Run a scan with the model unreachable: the catalog changes are committed and the response returns promptly; enrichment is retried later.

### User Story: Each AI result is saved on its own (Medium)

As the owner, I want each game's enrichment result saved separately, so that one bad answer or timeout affects only that game.

**Independent Test**: Make the model fail for one game in a batch: the others are saved.

### User Story: The monthly briefing does not hold a transaction while it waits (Low)

As the owner, I want the briefing's model call made without an open transaction, and only the result saved in one.

**Independent Test**: Briefing generation succeeds and fails without a connection held during the call.

---

## Requirements

- No model call MAY run while a database transaction is open (read-only transactions included).
- Enrichment MUST keep its attempt counters and drain behaviour: pending games are still picked up by later scans, syncs and manual refreshes.
- Each enrichment result MUST be persisted in its own short transaction.
- Scan and sync responses MUST NOT depend on the model's latency; the existing per-scan batch cap becomes a drain-rate choice, not a timeout guard.
- Behaviour visible to users MUST NOT change except faster scan responses.

## Out of Scope

Changing models or routing, new AI features, anything marked defer in the gap analysis.

## Clarifications Needed

- Run enrichment after the scan transaction commits on the same request thread, on an async worker, or as a scheduled drain? (the code comment says 'there is no scheduler' today)
- Is an outbox needed, or are the existing PENDING status and attempt counters enough? (the report recommends outbox plus idempotency keys; a status column may already be the outbox here)
