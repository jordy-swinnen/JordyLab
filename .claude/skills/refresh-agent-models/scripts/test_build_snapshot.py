"""Parsers in build_snapshot.py, checked on small synthetic pages (no network, no saved HTML)."""

from __future__ import annotations

import json

import pytest

import build_snapshot as snapshot

GO_DOCS_EXCERPT = """Usage limits
Usage limits are defined as monthly dollar amounts.
Model | Input | Output | Cached Read | Cached Write | Monthly limit |
Claude Haiku 5.5 (≤ 100K tokens) | $0.10 | $0.50 | $0.01 | $0.125 | $15 |
Claude Haiku 5.5 (> 100K tokens) | $0.50 | $2.50 | $0.05 | $0.625 | $15 |
Gemini 4 Argon (50% off) | $4.00 $2.00 | $20.00 $10.00 | $0.20 $0.10 | - | $30 |
DeepSeek V4.1 Flash (Off-Peak) | $0.15 | $0.60 | $0.003 | - | $100 |
DeepSeek V4.1 Flash (Peak) | $0.30 | $1.20 | $0.006 | - | $100 |
Beam Free | Free | Free | Free | - | Unlimited |
limited time |
Model | Input | Output | Cached Read | Cached Write | Monthly limit |
Claude Haiku 5.5 (≤ 100K tokens) | $0.10 | $0.50 | $0.01 | $0.125 | $60 |
Gemini 4 Argon: The 50% discount applies through November 30, 2026.
Estimated requests
Model | Requests per 5 hours | Requests per week | Requests per month |
Claude Haiku 5.5 | 3,850 | 9,620 | 19,230 |
Gemini 4 Argon | 440 | 1,080 | 2,120 |
DeepSeek V4.1 Flash | 43,330 | 108,330 | 216,670 |
Model | Requests per 5 hours | Requests per week | Requests per month |
Claude Haiku 5.5 | 19 | 19 | 19 |
"""

AA_PAYLOAD = (
    '{"slug":"claude-haiku-5-5","name":"Claude Haiku 5.5 (Max)","shortName":"Claude Haiku 5.5 (max)",'
    '"deprecated":false,"modelCreatorName":"Anthropic","intelligenceIndex":36.2,'
    '"intelligenceIndexCostPerTask":0.411,"price1mInputTokens":0.1,"price1mOutputTokens":0.5,'
    '"medianEndToEndResponseTimeSeconds":372.2,"scores":{"terminalbench-4-0":0.328,'
    '"frontiercode-2-0":0.349}}'
    '{"slug":"glm-5-2","name":"GLM-5.2","deprecated":true}'
)


@pytest.mark.parametrize(
    ("cell", "expected"),
    [("$4.00 $2.00", (4.0, 2.0)), ("$1,000", (1000.0,)), ("-", ()), ("Free", ())],
)
def test_parse_dollar_amounts_reads_every_amount_in_a_cell(
    cell: str, expected: tuple[float, ...]
) -> None:
    assert snapshot.parse_dollar_amounts(cell) == expected


@pytest.mark.parametrize(
    ("row_name", "expected"),
    [
        ("DeepSeek V4.1 Flash (Peak)", "DeepSeek V4.1 Flash"),
        ("Qwen3.7 Plus (≤ 256K tokens)", "Qwen3.7 Plus"),
    ],
)
def test_base_name_drops_the_tier_or_peak_suffix(row_name: str, expected: str) -> None:
    assert snapshot.base_name(row_name) == expected


def test_go_plan_entries_take_the_first_tier_and_the_peak_row() -> None:
    entries = {entry.go_id: entry for entry in snapshot.build_go_entries(GO_DOCS_EXCERPT)}
    haiku = entries["claude-haiku-5-5"]
    assert (haiku.monthly_limit, haiku.price_out, haiku.requests_per_month) == (15.0, 0.5, 19230)
    deepseek = entries["deepseek-v4.1-flash"]
    assert deepseek.peak_split is True
    assert deepseek.price_out == pytest.approx(1.2), "the Peak row, not Off-Peak"
    assert deepseek.requests_per_month == 216670


def test_gemini_promo_keeps_both_prices_and_the_end_date() -> None:
    gemini = {entry.go_id: entry for entry in snapshot.build_go_entries(GO_DOCS_EXCERPT)}[
        "gemini-4-argon"
    ]
    assert gemini.price_out == pytest.approx(20.0)
    assert gemini.promo is not None
    assert gemini.promo.price_out == pytest.approx(10.0)
    assert gemini.promo.ends == "2026-11-30"


def test_models_missing_from_the_docs_are_recorded_as_such() -> None:
    entries = {entry.go_id: entry for entry in snapshot.build_go_entries(GO_DOCS_EXCERPT)}
    assert entries["hy3"].note == "not in the Go docs"
    assert entries["hy3"].monthly_limit is None


def test_aa_objects_are_split_into_model_rows_and_minimal_references() -> None:
    rows, minimal = snapshot.parse_aa_objects(AA_PAYLOAD)
    assert "Claude Haiku 5.5 (max)" in rows
    assert "GLM-5.2" in minimal and minimal["GLM-5.2"]["deprecated"] is True
    record = snapshot.aa_record_from_object(rows["Claude Haiku 5.5 (max)"])
    assert record.intelligence_index == pytest.approx(36.2)
    assert record.terminal_bench_4 == pytest.approx(0.328)
    assert record.frontiercode_2 == pytest.approx(0.349)


def test_aa_payload_without_model_rows_is_a_source_error() -> None:
    with pytest.raises(snapshot.SnapshotSourceError):
        snapshot.parse_aa_objects("<html>the layout changed</html>")


def test_opencode_catalog_gives_the_variant_keys_per_model() -> None:
    catalog = (
        "opencode-go/claude-haiku-5-5\n"
        + json.dumps({"id": "claude-haiku-5-5", "variants": {"low": {}, "max": {}}})
        + "\nopencode-go/mimo-v2.6-flash\n"
        + json.dumps({"id": "mimo-v2.6-flash"})
        + "\n"
    )
    assert snapshot.parse_opencode_variants(catalog) == {
        "claude-haiku-5-5": ("low", "max"),
        "mimo-v2.6-flash": (),
    }


def test_empty_catalog_is_a_source_error() -> None:
    with pytest.raises(snapshot.SnapshotSourceError):
        snapshot.parse_opencode_variants("nothing here")


def test_a_missing_docs_section_is_a_source_error() -> None:
    with pytest.raises(snapshot.SnapshotSourceError):
        snapshot.build_go_entries("no tables on this page")
