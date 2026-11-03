"""Decision rules, checked against the 2026-11-03 snapshot (fixtures/snapshot-2026-11-03.json)."""

from __future__ import annotations

import datetime
import json
from collections.abc import Sequence
from pathlib import Path

import pytest

import decide

SCRIPTS_DIRECTORY = Path(__file__).resolve().parent
SNAPSHOT_PATH = SCRIPTS_DIRECTORY / "fixtures" / "snapshot-2026-11-03.json"
SETTINGS_PATH = SCRIPTS_DIRECTORY / "settings.json"

# The assignments before PR #179 switched the reviewer and worker on Claude Code.
PRE_SWITCH_INCUMBENTS = (
    'current.opencode.Reviewer.go_id="mimo-v2.6-pro"',
    "current.opencode.Reviewer.variant=null",
    'current.claude.Reviewer.model="claude-sonnet-5-5"',
    'current.claude.Reviewer.effort="high"',
    'current.claude.Worker.model="claude-sonnet-5-5"',
    'current.claude.Worker.effort="medium"',
)


def decide_with(overrides: Sequence[str] = (), cost_axis: str | None = None) -> decide.Decision:
    entries, records, as_of = decide.read_snapshot(SNAPSHOT_PATH)
    raw_settings = json.loads(SETTINGS_PATH.read_text(encoding="utf-8"))
    assignments = list(overrides)
    if cost_axis is not None:
        assignments.append(f"go.cost_axis={cost_axis}")
    settings = decide.load_settings(decide.apply_overrides(raw_settings, assignments))
    return decide.decide(entries, records, as_of, settings)


def opencode_picks(result: decide.Decision) -> dict[str, str | None]:
    return {role.role: role.pick.label if role.pick else None for role in result.opencode_roles}


def claude_picks(result: decide.Decision) -> dict[str, tuple[str | None, str | None]]:
    return {
        role.role: (role.pick.model, role.pick.effort) if role.pick else (None, None)
        for role in result.claude_roles
    }


def test_branch_state_reproduces_the_current_assignments() -> None:
    result = decide_with()
    assert opencode_picks(result) == {
        "Worker": "deepseek-v4.1-flash max",
        "Planner": "claude-haiku-5-5 xhigh",
        "Reviewer": "mimo-v2.6-flash",
    }
    assert claude_picks(result) == {
        "Planner": ("claude-opus-5-5", "medium"),
        "Reviewer": ("claude-haiku-5-5", "max"),
        "Worker": ("claude-haiku-5-5", "max"),
    }
    verdicts = [role.step5.verdict for role in (*result.opencode_roles, *result.claude_roles)]
    assert verdicts == ["unchanged"] * 6, (
        f"expected no switches on the branch state, got {verdicts}"
    )


def test_pre_switch_incumbents_show_the_three_switches() -> None:
    result = decide_with(PRE_SWITCH_INCUMBENTS)
    opencode = {role.role: role.step5.verdict for role in result.opencode_roles}
    claude = {role.role: role.step5.verdict for role in result.claude_roles}
    assert opencode["Reviewer"] == "switch: cost cut", f"got {opencode['Reviewer']}"
    assert claude["Reviewer"] == "switch: cost cut", f"got {claude['Reviewer']}"
    assert claude["Worker"] == "switch: gain", f"got {claude['Worker']}"


def test_lower_planner_lambda_picks_haiku_max_but_step5_keeps_opus() -> None:
    result = decide_with(["claude.lambda.index.Planner=0.15"])
    planner = next(role for role in result.claude_roles if role.role == "Planner")
    assert (planner.pick.model, planner.pick.effort) == ("claude-haiku-5-5", "max")
    assert planner.step5.verdict == "keep incumbent", f"got {planner.step5.verdict}"


def test_requests_axis_keeps_the_same_opencode_picks() -> None:
    assert opencode_picks(decide_with(cost_axis="requests")) == {
        "Worker": "deepseek-v4.1-flash max",
        "Planner": "claude-haiku-5-5 xhigh",
        "Reviewer": "mimo-v2.6-flash",
    }


def test_planner_first_would_leave_worker_with_no_candidate() -> None:
    result = decide_with(['go.role_order=["Planner", "Worker", "Reviewer"]'])
    roles = {role.role: role for role in result.opencode_roles}
    assert (
        roles["Planner"].pick is not None
        and roles["Planner"].pick.label == "deepseek-v4.1-flash max"
    )
    assert roles["Worker"].pick is None
    assert roles["Worker"].step5.verdict == "no eligible candidate"


def test_coding_frontier_drops_sonnet_high_because_haiku_max_scores_higher_for_less() -> None:
    result = decide_with()
    assert "Claude Sonnet 5.5 (high with fallback)" not in result.claude_coding_frontier
    assert "Claude Haiku 5.5 (max)" in result.claude_coding_frontier


def test_excluded_models_carry_their_reasons() -> None:
    excluded = {(item.model, item.variant): item for item in decide_with().excluded}
    glm_reasons = excluded[("glm-5.2", None)].reasons
    assert "deprecated on AA" in glm_reasons, glm_reasons
    assert any("requests 4,300 < 15,000" in reason for reason in glm_reasons), glm_reasons
    assert "no AA row" in excluded[("hy4-preview", None)].reasons


def test_gemini_uses_the_post_promo_list_price_when_the_promo_ends_within_45_days() -> None:
    gemini = next(c for c in decide_with().opencode_candidates if c.go_id == "gemini-4-argon")
    assert gemini.basis == "post-promo list"
    assert gemini.cost_per_task == pytest.approx(7.683, abs=0.01)
    assert gemini.requests_per_month == pytest.approx(1060)


def test_gemini_uses_the_promo_price_while_the_promo_is_far_off() -> None:
    entries, records, _ = decide.read_snapshot(SNAPSHOT_PATH)
    settings = decide.load_settings(json.loads(SETTINGS_PATH.read_text(encoding="utf-8")))
    result = decide.decide(entries, records, "2026-09-01", settings)
    gemini = next(c for c in result.opencode_candidates if c.go_id == "gemini-4-argon")
    assert gemini.basis == "promo"
    assert gemini.cost_per_task == pytest.approx(3.84, abs=0.01)
    assert gemini.requests_per_month == pytest.approx(2120)


def test_deepseek_is_priced_and_counted_at_peak() -> None:
    deepseek = next(
        c for c in decide_with().opencode_candidates if c.go_id == "deepseek-v4.1-flash"
    )
    assert deepseek.basis == "peak"
    assert deepseek.price_ratio == pytest.approx(1.0)
    assert deepseek.requests_per_month == pytest.approx(108335)


@pytest.mark.parametrize(
    ("effort", "variants", "expected"),
    [
        ("max", ["low", "high", "max"], ("max", None)),
        (None, ["low", "high", "max"], ("high", None)),
        (None, [], (None, None)),
        ("non-reasoning", ["low", "high"], (None, "non-reasoning row")),
        (
            "medium",
            ["low", "high"],
            (None, "effort medium is not an OpenCode variant of this model"),
        ),
    ],
)
def test_resolve_variant_maps_aa_rows_to_opencode_variants(
    effort: str | None, variants: list[str], expected: tuple[str | None, str | None]
) -> None:
    assert decide.resolve_variant(effort, variants) == expected


@pytest.mark.parametrize(
    ("row_name", "expected"),
    [
        ("Claude Opus 5.5 (max with fallback)", "max"),
        ("GLM-5.2 (Non-reasoning)", "non-reasoning"),
        ("Qwen3.8 Max (0902)", None),
        ("GLM-5.3-Flash", None),
    ],
)
def test_effort_of_reads_only_known_effort_words(row_name: str, expected: str | None) -> None:
    assert decide.effort_of(row_name) == expected


def test_pareto_front_keeps_only_items_nothing_beats_on_both_counts() -> None:
    points = [("cheap-weak", 1.0, 10.0), ("dear-strong", 5.0, 50.0), ("dear-weak", 5.0, 12.0)]
    front = decide.pareto_front(points, score=lambda point: point[2], cost=lambda point: point[1])
    assert [point[0] for point in front] == ["cheap-weak", "dear-strong"]


def test_apply_overrides_sets_nested_keys_and_bare_words() -> None:
    settings = {"claude": {"lambda": {"index": {"Planner": 0.2}}}, "go": {"cost_axis": "tasks"}}
    updated = decide.apply_overrides(
        settings, ["claude.lambda.index.Planner=0.15", "go.cost_axis=requests"]
    )
    assert updated["claude"]["lambda"]["index"]["Planner"] == 0.15
    assert updated["go"]["cost_axis"] == "requests"
    assert settings["go"]["cost_axis"] == "tasks", "the input must not be mutated"


def test_apply_overrides_rejects_an_assignment_without_a_value() -> None:
    with pytest.raises(decide.SettingsError):
        decide.apply_overrides({}, ["claude.lambda.index.Planner"])


def test_snapshot_is_dated_for_the_2026_11_03_run() -> None:
    _, _, as_of = decide.read_snapshot(SNAPSHOT_PATH)
    assert datetime.date.fromisoformat(as_of) == datetime.date(2026, 11, 3)
