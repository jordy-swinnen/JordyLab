#!/usr/bin/env python3
"""Choose OpenCode Go and Claude Code models from a snapshot and the judgement settings.

Inputs: the snapshot from build_snapshot.py, and settings.json (the judgement parameters and the
current assignments). Output: the picks, the walks, the Step 5 verdicts against the current
assignments, the frontiers and the exclusions, as JSON (default) or Markdown (--markdown).
Nothing is written to the repository.

Change a judgement for one run without editing the file:
    python3 decide.py --snapshot snapshot.json --set claude.lambda.index.Planner=0.15
    python3 decide.py --snapshot snapshot.json --cost-axis requests --markdown
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime
import functools
import json
import logging
import math
import re
import sys
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path
from typing import Any, TypeVar

from build_snapshot import AaRecord, GoPlanEntry, PromoTerms

logger = logging.getLogger(__name__)

SCRIPT_DIRECTORY = Path(__file__).resolve().parent
DEFAULT_SETTINGS_PATH = SCRIPT_DIRECTORY / "settings.json"
EFFORT_ORDER = ("low", "medium", "high", "xhigh", "max")
RECOGNISED_EFFORTS = frozenset(EFFORT_ORDER) | {"minimal", "none", "non-reasoning"}
EFFORT_IN_NAME = re.compile(r"\(([\w-]+)")
CLAUDE_FAMILY = re.compile(r"(Claude \w+ [\d.]+)")
CODING_CLAUSE_TOLERANCE = 3.0

ItemType = TypeVar("ItemType")


class SettingsError(Exception):
    """settings.json is missing a key or has a value the rules cannot use."""


@dataclasses.dataclass(frozen=True)
class GoJudgement:
    cost_axis: str
    role_order: tuple[str, ...]
    requests_floor: Mapping[str, float]
    tasks_floor: Mapping[str, float]
    response_cap_seconds: Mapping[str, float]
    points_per_decade: Mapping[str, float]
    margin: float
    cost_ratio_max: float
    promo_window_days: int
    peak_request_factor: float


@dataclasses.dataclass(frozen=True)
class ClaudeJudgement:
    metric_by_role: Mapping[str, str]
    lambda_by_metric: Mapping[str, Mapping[str, float]]
    margin: float
    coding_clause_points: float
    cost_ratio_max: float


@dataclasses.dataclass(frozen=True)
class Settings:
    go: GoJudgement
    claude: ClaudeJudgement
    current_opencode: Mapping[str, Mapping[str, Any]]
    current_claude: Mapping[str, Mapping[str, str]]


@dataclasses.dataclass(frozen=True)
class GoCandidate:
    """One OpenCode Go (model, variant) pair that has an AA index and cost per task."""

    go_id: str
    variant: str | None
    aa_name: str
    creator: str
    index: float
    cost_per_task: float
    tasks_per_month: float
    requests_per_month: float
    end_to_end_seconds: float | None
    basis: str
    price_ratio: float

    @property
    def label(self) -> str:
        return self.go_id if self.variant is None else f"{self.go_id} {self.variant}"

    def is_same_model(self, other: GoCandidate) -> bool:
        return self.go_id == other.go_id and self.variant == other.variant


@dataclasses.dataclass(frozen=True)
class ExcludedCandidate:
    model: str
    variant: str | None
    aa_name: str | None
    reasons: tuple[str, ...]


@dataclasses.dataclass(frozen=True)
class ClaudeRow:
    """One Anthropic row from AA, with the effort parsed from its name."""

    name: str
    family: str
    model: str
    effort: str | None
    index: float
    cost_per_task: float
    coding: float | None
    end_to_end_seconds: float | None

    def score(self, metric: str) -> float | None:
        return self.index if metric == "index" else self.coding


@dataclasses.dataclass(frozen=True)
class Step5Verdict:
    role: str
    incumbent: str
    pick: str | None
    verdict: str
    detail: str


@dataclasses.dataclass(frozen=True)
class GoRoleResult:
    role: str
    pick: GoCandidate | None
    walk: tuple[str, ...]
    eligible: tuple[str, ...]
    step5: Step5Verdict


@dataclasses.dataclass(frozen=True)
class ClaudeRoleResult:
    role: str
    metric: str
    lambda_value: float
    pick: ClaudeRow | None
    walk: tuple[str, ...]
    step5: Step5Verdict


@dataclasses.dataclass(frozen=True)
class Decision:
    as_of: str
    cost_axis: str
    opencode_roles: tuple[GoRoleResult, ...]
    opencode_candidates: tuple[GoCandidate, ...]
    opencode_frontier: tuple[str, ...]
    excluded: tuple[ExcludedCandidate, ...]
    claude_roles: tuple[ClaudeRoleResult, ...]
    claude_index_frontier: tuple[str, ...]
    claude_coding_frontier: tuple[str, ...]


def load_settings(raw: Mapping[str, Any]) -> Settings:
    try:
        go = raw["go"]
        claude = raw["claude"]
        current = raw["current"]

        return Settings(
            go=GoJudgement(
                cost_axis=go["cost_axis"],
                role_order=tuple(go["role_order"]),
                requests_floor=go["requests_floor"],
                tasks_floor=go["tasks_floor"],
                response_cap_seconds=go["response_cap_seconds"],
                points_per_decade=go["points_per_decade"],
                margin=float(go["margin"]),
                cost_ratio_max=float(go["cost_ratio_max"]),
                promo_window_days=int(go["promo_window_days"]),
                peak_request_factor=float(go["peak_request_factor"]),
            ),
            claude=ClaudeJudgement(
                metric_by_role=claude["metric_by_role"],
                lambda_by_metric=claude["lambda"],
                margin=float(claude["margin"]),
                coding_clause_points=float(claude["coding_clause_points"]),
                cost_ratio_max=float(claude["cost_ratio_max"]),
            ),
            current_opencode=current["opencode"],
            current_claude=current["claude"],
        )
    except KeyError as error:
        raise SettingsError(f"settings.json has no key {error}") from error


def apply_overrides(raw: Mapping[str, Any], assignments: Sequence[str]) -> dict[str, Any]:
    """Apply PATH=VALUE pairs, e.g. claude.lambda.index.Planner=0.15. VALUE is JSON or a bare word."""
    updated: dict[str, Any] = json.loads(json.dumps(raw))
    for assignment in assignments:
        path, separator, text = assignment.partition("=")
        if not separator:
            raise SettingsError(f"--set needs PATH=VALUE, got {assignment!r}")
        keys = path.split(".")
        node: Any = updated
        for key in keys[:-1]:
            if not isinstance(node, dict) or key not in node:
                raise SettingsError(f"--set path {path!r} has no key {key!r}")
            node = node[key]
        final_key = keys[-1]
        if not isinstance(node, dict) or final_key not in node:
            raise SettingsError(f"--set path {path!r} has no key {final_key!r}")
        try:
            value = json.loads(text)
        except ValueError:
            value = text
        node[final_key] = value

    return updated


def effort_of(name: str) -> str | None:
    match = EFFORT_IN_NAME.search(name)
    if match and match.group(1).lower() in RECOGNISED_EFFORTS:
        return match.group(1).lower()

    return None


def resolve_variant(effort: str | None, variants: Sequence[str]) -> tuple[str | None, str | None]:
    """The OpenCode variant for an AA row, or the reason the row cannot be used."""
    if effort in ("non-reasoning", "none"):
        return None, "non-reasoning row"
    if effort is not None:
        if effort in variants:
            return effort, None

        return None, f"effort {effort} is not an OpenCode variant of this model"
    if not variants:
        return None, None
    if "high" in variants:
        return "high", None

    return None, "unlabelled AA row and no high variant"


def go_plan_basis(
    entry: GoPlanEntry, as_of: datetime.date, judgement: GoJudgement
) -> tuple[float, float, str]:
    """Output price and requests a month that apply on this date (list, promo, post-promo or peak)."""
    price_out = entry.price_out or 0.0
    requests = float(entry.requests_per_month or 0)
    promo: PromoTerms | None = entry.promo
    if promo is not None:
        days_left = (datetime.date.fromisoformat(promo.ends) - as_of).days if promo.ends else 0
        if days_left <= judgement.promo_window_days:
            requests = requests * (promo.price_out or 0.0) / price_out

            return price_out, requests, "post-promo list"

        return promo.price_out or price_out, requests, "promo"
    if entry.peak_split:
        return price_out, requests * judgement.peak_request_factor, "peak"

    return price_out, requests, "list"


def row_problems(record: AaRecord, variant_problem: str | None) -> list[str]:
    problems = []
    if record.deprecated:
        problems.append("deprecated on AA")
    if record.intelligence_index is None:
        problems.append("no AA index")
    if record.cost_per_task is None:
        problems.append("no AA cost per task")
    if variant_problem:
        problems.append(variant_problem)

    return problems


def scan_go_entry(
    entry: GoPlanEntry,
    records: Mapping[str, AaRecord],
    as_of: datetime.date,
    judgement: GoJudgement,
    seen: set[tuple[str, str | None]],
) -> tuple[list[GoCandidate], list[ExcludedCandidate], list[str]]:
    """Candidates from one Go model, the rows that were rejected, and reasons that block the whole model."""
    if entry.variants is None:
        return [], [], ["not in the OpenCode catalog"]
    if not entry.aa_rows:
        return [], [], [entry.note or "no AA row"]
    if entry.monthly_limit is None or entry.price_out is None or entry.requests_per_month is None:
        return [], [], ["the Go docs have no limit, price or request count"]
    price_out, requests, basis = go_plan_basis(entry, as_of, judgement)
    found: list[GoCandidate] = []
    rejected: list[ExcludedCandidate] = []
    for aa_name in entry.aa_rows:
        record = records.get(aa_name)
        if record is None:
            continue
        variant, variant_problem = resolve_variant(effort_of(aa_name), entry.variants)
        problems = row_problems(record, variant_problem)
        if problems:
            rejected.append(ExcludedCandidate(entry.go_id, variant, aa_name, tuple(problems)))
            continue
        if (entry.go_id, variant) in seen:
            continue
        seen.add((entry.go_id, variant))
        ratio = price_out / record.price_out if record.price_out else 1.0
        cost = record.cost_per_task * ratio if record.cost_per_task is not None else math.nan
        found.append(
            GoCandidate(
                go_id=entry.go_id,
                variant=variant,
                aa_name=aa_name,
                creator=entry.creator,
                index=record.intelligence_index or 0.0,
                cost_per_task=cost,
                tasks_per_month=entry.monthly_limit / cost,
                requests_per_month=requests,
                end_to_end_seconds=record.end_to_end_seconds,
                basis=basis,
                price_ratio=ratio,
            )
        )

    return found, rejected, []


def build_go_candidates(
    entries: Sequence[GoPlanEntry],
    records: Mapping[str, AaRecord],
    as_of: datetime.date,
    judgement: GoJudgement,
) -> tuple[list[GoCandidate], list[ExcludedCandidate]]:
    """Candidates for every Go model. A model with no candidate gets one exclusion with all its reasons."""
    candidates: list[GoCandidate] = []
    excluded: list[ExcludedCandidate] = []
    seen: set[tuple[str, str | None]] = set()
    for entry in entries:
        found, rejected, blocking = scan_go_entry(entry, records, as_of, judgement, seen)
        candidates.extend(found)
        if found:
            excluded.extend(rejected)
            continue
        reasons = list(blocking) + [reason for row in rejected for reason in row.reasons]
        reasons += request_floor_reasons(entry, judgement)
        if reasons:
            excluded.append(
                ExcludedCandidate(entry.go_id, None, None, tuple(dict.fromkeys(reasons)))
            )

    return candidates, excluded


def floor_reasons(candidate: GoCandidate, role: str, judgement: GoJudgement) -> list[str]:
    reasons = []
    if candidate.requests_per_month < judgement.requests_floor[role]:
        reasons.append(
            f"{role}: requests {candidate.requests_per_month:,.0f} < {judgement.requests_floor[role]:,.0f}"
        )
    if candidate.tasks_per_month < judgement.tasks_floor[role]:
        reasons.append(
            f"{role}: tasks {candidate.tasks_per_month:.1f} < {judgement.tasks_floor[role]:g}"
        )
    cap = judgement.response_cap_seconds[role]
    if candidate.end_to_end_seconds is not None and candidate.end_to_end_seconds > cap:
        reasons.append(f"{role}: response {candidate.end_to_end_seconds:.0f} s > {cap:g} s")

    return reasons


def value_of(candidate: GoCandidate, cost_axis: str) -> float:
    return candidate.tasks_per_month if cost_axis == "tasks" else candidate.requests_per_month


def go_walk(
    pool: Sequence[GoCandidate], role: str, judgement: GoJudgement
) -> tuple[GoCandidate, tuple[GoCandidate, ...]]:
    axis = judgement.cost_axis
    current = max(pool, key=lambda candidate: (value_of(candidate, axis), candidate.index))
    path = [current]
    while True:
        steps = [
            candidate
            for candidate in pool
            if candidate.index >= current.index + judgement.margin
            and value_of(candidate, axis) < value_of(current, axis)
        ]
        if not steps:
            break
        best = max(
            steps,
            key=lambda candidate: (
                (candidate.index - current.index)
                / math.log10(value_of(current, axis) / value_of(candidate, axis))
            ),
        )
        ratio = (best.index - current.index) / math.log10(
            value_of(current, axis) / value_of(best, axis)
        )
        if ratio < judgement.points_per_decade[role]:
            break
        current = best
        path.append(best)

    return current, tuple(path)


def go_step5(
    role: str,
    pick: GoCandidate | None,
    incumbent_key: tuple[str, str | None],
    candidates: Sequence[GoCandidate],
    judgement: GoJudgement,
) -> Step5Verdict:
    incumbent_label = (
        incumbent_key[0] if incumbent_key[1] is None else f"{incumbent_key[0]} {incumbent_key[1]}"
    )
    incumbent = next(
        (
            candidate
            for candidate in candidates
            if (candidate.go_id, candidate.variant) == incumbent_key
        ),
        None,
    )
    if pick is None:
        return Step5Verdict(role, incumbent_label, None, "no eligible candidate", "")
    if incumbent is None or floor_reasons(incumbent, role, judgement):
        return Step5Verdict(
            role, incumbent_label, pick.label, "forced: incumbent no longer eligible", ""
        )
    if pick.is_same_model(incumbent):
        return Step5Verdict(role, incumbent_label, pick.label, "unchanged", "")
    axis = judgement.cost_axis
    pick_value, incumbent_value = value_of(pick, axis), value_of(incumbent, axis)
    detail = (
        f"index {pick.index:.1f} vs {incumbent.index:.1f}; "
        f"{axis} {pick_value:,.1f} vs {incumbent_value:,.1f}"
    )
    if (
        pick.index >= incumbent.index + judgement.margin
        and pick_value >= incumbent_value / judgement.cost_ratio_max
    ):
        return Step5Verdict(role, incumbent_label, pick.label, "switch: gain", detail)
    if pick_value > incumbent_value and pick.index >= incumbent.index - judgement.margin:
        return Step5Verdict(role, incumbent_label, pick.label, "switch: cost cut", detail)

    return Step5Verdict(role, incumbent_label, pick.label, "keep incumbent", detail)


def choose_opencode(
    candidates: Sequence[GoCandidate],
    settings: Settings,
) -> tuple[GoRoleResult, ...]:
    judgement = settings.go
    used: set[str] = set()
    planner_creator: str | None = None
    picks: dict[
        str, tuple[GoCandidate | None, tuple[GoCandidate, ...], tuple[GoCandidate, ...]]
    ] = {}
    for role in judgement.role_order:
        pool = [
            candidate
            for candidate in candidates
            if candidate.go_id not in used
            and not floor_reasons(candidate, role, judgement)
            and not (
                role == "Reviewer"
                and planner_creator is not None
                and candidate.creator == planner_creator
            )
        ]
        if not pool:
            picks[role] = (None, (), tuple(pool))
            continue
        pick, path = go_walk(pool, role, judgement)
        picks[role] = (pick, path, tuple(pool))
        used.add(pick.go_id)
        if role == "Planner":
            planner_creator = pick.creator
    results = []
    for role in judgement.role_order:
        pick, path, pool = picks[role]
        incumbent_key = _incumbent_key(settings.current_opencode[role])
        results.append(
            GoRoleResult(
                role=role,
                pick=pick,
                walk=tuple(step.label for step in path),
                eligible=tuple(candidate.label for candidate in pool),
                step5=go_step5(role, pick, incumbent_key, candidates, judgement),
            )
        )

    return tuple(results)


def claude_rows(records: Sequence[AaRecord]) -> list[ClaudeRow]:
    rows = []
    for record in records:
        if (
            record.creator != "Anthropic"
            or record.cost_per_task is None
            or record.intelligence_index is None
        ):
            continue
        family_match = CLAUDE_FAMILY.match(record.name)
        if family_match is None:
            continue
        family = family_match.group(1)
        coding = None
        if record.terminal_bench_4 is not None and record.frontiercode_2 is not None:
            coding = 0.6 * record.frontiercode_2 * 100 + 0.4 * record.terminal_bench_4 * 100
        rows.append(
            ClaudeRow(
                name=record.name,
                family=family,
                model=family.lower().replace(" ", "-").replace(".", "-"),
                effort=effort_of(record.name),
                index=record.intelligence_index,
                cost_per_task=record.cost_per_task,
                coding=coding,
                end_to_end_seconds=record.end_to_end_seconds,
            )
        )

    return rows


def claude_slope(base: ClaudeRow, other: ClaudeRow, metric: str) -> float:
    gain = (other.score(metric) or 0.0) - (base.score(metric) or 0.0)
    if gain <= 0:
        return math.inf

    return (other.cost_per_task - base.cost_per_task) / gain


def claude_walk(
    rows: Sequence[ClaudeRow], metric: str, lambda_value: float
) -> tuple[ClaudeRow, tuple[ClaudeRow, ...]]:
    scored = [row for row in rows if row.score(metric) is not None]
    current = min(scored, key=lambda row: (row.cost_per_task, -(row.score(metric) or 0.0)))
    path = [current]
    while True:
        higher = [
            row for row in scored if (row.score(metric) or 0.0) > (current.score(metric) or 0.0)
        ]
        if not higher:
            break
        best = min(higher, key=lambda row: claude_slope(current, row, metric))
        if claude_slope(current, best, metric) > lambda_value:
            break
        current = best
        path.append(best)
    family = sorted(
        (row for row in scored if row.family == current.family and row.effort in EFFORT_ORDER),
        key=lambda row: EFFORT_ORDER.index(row.effort or "low"),
    )
    ladder = family[0]
    for higher_effort in family[1:]:
        if (higher_effort.score(metric) or 0.0) > (ladder.score(metric) or 0.0) and claude_slope(
            ladder, higher_effort, metric
        ) <= lambda_value:
            ladder = higher_effort
        else:
            break

    return ladder, tuple(path)


def claude_step5(
    role: str,
    pick: ClaudeRow,
    incumbent: ClaudeRow | None,
    metric: str,
    judgement: ClaudeJudgement,
) -> Step5Verdict:
    if incumbent is None:
        return Step5Verdict(
            role, "not in the snapshot", pick.name, "forced: incumbent not in the snapshot", ""
        )
    if pick.name == incumbent.name:
        return Step5Verdict(role, incumbent.name, pick.name, "unchanged", "")
    pick_score = pick.score(metric) or 0.0
    incumbent_score = incumbent.score(metric) or 0.0
    detail = f"{metric} {pick_score:.1f} vs {incumbent_score:.1f}; ${pick.cost_per_task:.3f} vs ${incumbent.cost_per_task:.3f}"
    cheap_enough = pick.cost_per_task <= judgement.cost_ratio_max * incumbent.cost_per_task
    if pick_score >= incumbent_score + judgement.margin and cheap_enough:
        return Step5Verdict(role, incumbent.name, pick.name, "switch: gain", detail)
    if (
        pick.cost_per_task < incumbent.cost_per_task
        and pick_score >= incumbent_score - judgement.margin
    ):
        return Step5Verdict(role, incumbent.name, pick.name, "switch: cost cut", detail)
    if (
        metric == "coding"
        and pick_score >= incumbent_score + judgement.coding_clause_points
        and cheap_enough
    ):
        return Step5Verdict(role, incumbent.name, pick.name, "switch: coding evidence", detail)

    return Step5Verdict(role, incumbent.name, pick.name, "keep incumbent", detail)


def choose_claude(rows: Sequence[ClaudeRow], settings: Settings) -> tuple[ClaudeRoleResult, ...]:
    judgement = settings.claude
    results = []
    for role, metric in judgement.metric_by_role.items():
        lambda_value = judgement.lambda_by_metric[metric][role]
        pick, path = claude_walk(rows, metric, lambda_value)
        spec = settings.current_claude[role]
        incumbent = next(
            (row for row in rows if row.model == spec["model"] and row.effort == spec["effort"]),
            None,
        )
        results.append(
            ClaudeRoleResult(
                role=role,
                metric=metric,
                lambda_value=lambda_value,
                pick=pick,
                walk=tuple(row.name for row in path),
                step5=claude_step5(role, pick, incumbent, metric, judgement),
            )
        )

    return tuple(results)


def pareto_front(
    items: Sequence[ItemType], score: Callable[[ItemType], float], cost: Callable[[ItemType], float]
) -> list[ItemType]:
    """Items no other item beats on both: not costlier, and scoring at least as well."""
    front: list[ItemType] = []
    best_score = -math.inf
    for item in sorted(items, key=lambda entry: (cost(entry), -score(entry))):
        if score(item) > best_score:
            front.append(item)
            best_score = score(item)

    return front


def go_candidate_index(candidate: GoCandidate) -> float:
    return candidate.index


def go_candidate_frontier_cost(candidate: GoCandidate, cost_axis: str) -> float:
    return -value_of(candidate, cost_axis)


def claude_row_index(row: ClaudeRow) -> float:
    return row.index


def claude_row_coding(row: ClaudeRow) -> float:
    return row.coding or 0.0


def claude_row_cost(row: ClaudeRow) -> float:
    return row.cost_per_task


def request_floor_reasons(entry: GoPlanEntry, judgement: GoJudgement) -> list[str]:
    if entry.requests_per_month is None:
        return []

    return [
        f"{role}: requests {entry.requests_per_month:,} < {floor:,.0f}"
        for role, floor in judgement.requests_floor.items()
        if entry.requests_per_month < floor
    ]


def decide(
    entries: Sequence[GoPlanEntry],
    records: Sequence[AaRecord],
    as_of: str,
    settings: Settings,
) -> Decision:
    day = datetime.date.fromisoformat(as_of)
    records_by_name = {record.name: record for record in records}
    candidates, excluded = build_go_candidates(entries, records_by_name, day, settings.go)
    opencode_roles = choose_opencode(candidates, settings)
    for candidate in candidates:
        if all(floor_reasons(candidate, role, settings.go) for role in settings.go.role_order):
            reasons = tuple(
                reason
                for role in settings.go.role_order
                for reason in floor_reasons(candidate, role, settings.go)
            )
            excluded.append(
                ExcludedCandidate(candidate.go_id, candidate.variant, candidate.aa_name, reasons)
            )
    value_axis = settings.go.cost_axis
    go_front = pareto_front(
        candidates,
        score=go_candidate_index,
        cost=functools.partial(go_candidate_frontier_cost, cost_axis=value_axis),
    )
    rows = claude_rows(records)
    claude_roles = choose_claude(rows, settings)
    index_front = pareto_front(rows, score=claude_row_index, cost=claude_row_cost)
    coding_rows = [row for row in rows if row.coding is not None]
    coding_front = pareto_front(coding_rows, score=claude_row_coding, cost=claude_row_cost)

    return Decision(
        as_of=as_of,
        cost_axis=value_axis,
        opencode_roles=opencode_roles,
        opencode_candidates=tuple(candidates),
        opencode_frontier=tuple(candidate.label for candidate in go_front),
        excluded=tuple(excluded),
        claude_roles=claude_roles,
        claude_index_frontier=tuple(row.name for row in index_front),
        claude_coding_frontier=tuple(row.name for row in coding_front),
    )


def render_markdown(decision: Decision) -> str:
    lines = [
        f"## OpenCode Go (as of {decision.as_of}, cost axis: {decision.cost_axis})",
        "",
        "| Role | Pick | Index | $ per AA task | Tasks/mo | Requests/mo | Step 5 |",
        "|---|---|---|---|---|---|---|",
    ]
    for role_result in decision.opencode_roles:
        pick = role_result.pick
        if pick is None:
            lines.append(f"| {role_result.role} | none | | | | | {role_result.step5.verdict} |")
            continue
        lines.append(
            f"| {role_result.role} | {pick.label} | {pick.index:.1f} | {pick.cost_per_task:.3f} | "
            f"{pick.tasks_per_month:.1f} | {pick.requests_per_month:,.0f} | {role_result.step5.verdict} |"
        )
    lines += [
        "",
        "## Claude Code",
        "",
        "| Role | Metric | Pick | Score | $ per AA task | λ | Step 5 |",
        "|---|---|---|---|---|---|---|",
    ]
    for role_result in decision.claude_roles:
        pick = role_result.pick
        if pick is None:
            continue
        score = pick.score(role_result.metric) or 0.0
        lines.append(
            f"| {role_result.role} | {role_result.metric} | {pick.model} {pick.effort or ''} | {score:.1f} | "
            f"{pick.cost_per_task:.3f} | {role_result.lambda_value} | {role_result.step5.verdict} |"
        )
    lines += ["", "## Frontiers", "", "OpenCode Go: " + ", ".join(decision.opencode_frontier)]
    lines += ["", "Claude index: " + ", ".join(decision.claude_index_frontier)]
    lines += ["", "Claude coding: " + ", ".join(decision.claude_coding_frontier)]
    lines += ["", "## Excluded", ""]
    for exclusion in decision.excluded:
        name = (
            exclusion.model
            if exclusion.variant is None
            else f"{exclusion.model} {exclusion.variant}"
        )
        lines.append(f"- `{name}`: " + "; ".join(exclusion.reasons))

    return "\n".join(lines) + "\n"


def read_snapshot(snapshot_path: Path) -> tuple[list[GoPlanEntry], list[AaRecord], str]:
    """Entries, AA records and the as-of date from a snapshot written by build_snapshot.py."""
    snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
    entries = [_entry_from_json(item) for item in snapshot["go"]]
    records = [_record_from_json(item) for item in snapshot["aa"]]

    return entries, records, snapshot["as_of"]


def run(
    snapshot_path: Path, settings_path: Path, assignments: Sequence[str], cost_axis: str | None
) -> Decision:
    raw_settings = json.loads(settings_path.read_text(encoding="utf-8"))
    overrides = list(assignments)
    if cost_axis is not None:
        overrides.append(f"go.cost_axis={cost_axis}")
    settings = load_settings(apply_overrides(raw_settings, overrides))
    entries, records, as_of = read_snapshot(snapshot_path)

    return decide(entries, records, as_of, settings)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Choose OpenCode Go and Claude Code models from a snapshot."
    )
    parser.add_argument(
        "--snapshot", type=Path, required=True, help="snapshot JSON from build_snapshot.py"
    )
    parser.add_argument(
        "--settings", type=Path, default=DEFAULT_SETTINGS_PATH, help="judgement settings JSON"
    )
    parser.add_argument(
        "--set",
        action="append",
        default=[],
        metavar="PATH=VALUE",
        help="override one setting for this run, e.g. claude.lambda.index.Planner=0.15",
    )
    parser.add_argument(
        "--cost-axis", choices=("tasks", "requests"), help="Go cost axis for this run"
    )
    parser.add_argument(
        "--markdown", action="store_true", help="print a Markdown report instead of JSON"
    )
    arguments = parser.parse_args(argv)
    decision = run(arguments.snapshot, arguments.settings, arguments.set, arguments.cost_axis)
    if arguments.markdown:
        print(render_markdown(decision), end="")
    else:
        print(json.dumps(dataclasses.asdict(decision), indent=2, ensure_ascii=False))

    return 0


def _incumbent_key(spec: Mapping[str, Any]) -> tuple[str, str | None]:
    return spec["go_id"], spec.get("variant")


def _entry_from_json(item: Mapping[str, Any]) -> GoPlanEntry:
    promo = item.get("promo")

    return GoPlanEntry(
        go_id=item["go_id"],
        docs_name=item["docs_name"],
        creator=item["creator"],
        monthly_limit=item["monthly_limit"],
        price_in=item["price_in"],
        price_out=item["price_out"],
        peak_split=item["peak_split"],
        promo=None
        if promo is None
        else PromoTerms(promo["price_in"], promo["price_out"], promo["ends"]),
        requests_per_month=item["requests_per_month"],
        variants=None if item["variants"] is None else tuple(item["variants"]),
        aa_rows=tuple(item["aa_rows"]),
        note=item["note"],
    )


def _record_from_json(item: Mapping[str, Any]) -> AaRecord:
    return AaRecord(
        name=item["name"],
        creator=item["creator"],
        deprecated=item["deprecated"],
        intelligence_index=item["intelligence_index"],
        cost_per_task=item["cost_per_task"],
        price_in=item["price_in"],
        price_out=item["price_out"],
        end_to_end_seconds=item["end_to_end_seconds"],
        terminal_bench_4=item["terminal_bench_4"],
        frontiercode_2=item["frontiercode_2"],
    )


if __name__ == "__main__":
    logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(message)s")
    sys.exit(main())
