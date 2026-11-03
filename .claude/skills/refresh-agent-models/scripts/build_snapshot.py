#!/usr/bin/env python3
"""Snapshot the sources that refresh-agent-models decides from.

Reads the Artificial Analysis leaderboard (the page's embedded data), the OpenCode Go plan page
and the OpenCode model catalog, and writes one JSON file for decide.py. This module parses numbers
and matches names. It makes no judgement about which model to pick.

Examples:
    python3 build_snapshot.py --fetch --out snapshot.json
    python3 build_snapshot.py --aa-html aa.html --go-html go.html --out snapshot.json
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime
import html
import json
import logging
import re
import subprocess
import sys
import urllib.request
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

AA_LEADERBOARD_URL = "https://artificialanalysis.ai/leaderboards/models"
GO_DOCS_URL = "https://opencode.ai/v2/docs/console/go"
OPENCODE_CATALOG_COMMAND = ("opencode", "models", "opencode-go", "--verbose")
MODEL_OBJECT_START = '{"slug":"'
DOLLAR_AMOUNT = re.compile(r"\$([\d.,]+)")
PROMO_END_SENTENCE = re.compile(
    r"([A-Za-z0-9 .\-]+?):\s*The 50% discount applies through ([A-Za-z]+ \d{1,2}, \d{4})"
)
CATALOG_MODEL_LINE = re.compile(r"^opencode-go/(\S+)\s*$", re.MULTILINE)


class SnapshotSourceError(Exception):
    """A source page or catalog did not have the structure the parser expects."""


@dataclasses.dataclass(frozen=True)
class GoModelSpec:
    """One OpenCode Go model: its id, its Go docs row name, its creator and its AA name pattern."""

    go_id: str
    docs_name: str
    creator: str
    aa_name_pattern: str | None


GO_MODEL_SPECS: tuple[GoModelSpec, ...] = (
    GoModelSpec(
        "claude-haiku-5-5", "Claude Haiku 5.5", "Anthropic", r"^Claude Haiku 5\.5 \((\w+)\)$"
    ),
    GoModelSpec(
        "deepseek-v4.1-flash",
        "DeepSeek V4.1 Flash",
        "DeepSeek",
        r"^DeepSeek V4\.1 Flash 1017 \((\w+)\)$",
    ),
    GoModelSpec("glm-5.3-flash", "GLM-5.3-Flash", "Z AI", r"^GLM-5\.3-Flash$"),
    GoModelSpec("glm-5.3", "GLM-5.3", "Z AI", r"^GLM-5\.3 \((\w+)\)$"),
    GoModelSpec("glm-5.2", "GLM-5.2", "Z AI", r"^GLM-5\.2$"),
    GoModelSpec("kimi-k3.1-pro", "Kimi K3.1 Pro", "Kimi", r"^Kimi K3\.1 Pro$"),
    GoModelSpec("kimi-k3", "Kimi K3", "Kimi", r"^Kimi K3( \((\w+)\))?$"),
    GoModelSpec("kimi-k2.7-code", "Kimi K2.7 Code", "Kimi", r"^Kimi K2\.7 Code$"),
    GoModelSpec("longcat-2.0", "LongCat-2.0", "Meituan", r"^LongCat 2\.0$"),
    GoModelSpec("step-5-preview", "Step 5 Preview", "StepFun", r"^Step 5 Preview$"),
    GoModelSpec("mimo-v2.6-flash", "MiMo-V2.6-Flash", "Xiaomi", r"^MiMo-V2\.6-Flash$"),
    GoModelSpec("mimo-v2.6-pro", "MiMo-V2.6-Pro", "Xiaomi", r"^MiMo-V2\.6-Pro$"),
    GoModelSpec("minimax-m3.1-flash", "MiniMax M3.1 Flash", "MiniMax", r"^MiniMax-M3\.1-Flash$"),
    GoModelSpec("minimax-m3", "MiniMax M3", "MiniMax", r"^MiniMax-M3$"),
    GoModelSpec("qwen3.8-max", "Qwen3.8 Max", "Alibaba", r"^Qwen3\.8 Max"),
    GoModelSpec("qwen3.7-plus", "Qwen3.7 Plus", "Alibaba", r"^Qwen3\.7 Plus$"),
    GoModelSpec("qwen3.8-flash", "Qwen3.8 Flash", "Alibaba", r"^Qwen3\.8-Flash-Next$"),
    GoModelSpec(
        "deepseek-v4-pro", "DeepSeek V4 Pro", "DeepSeek", r"^DeepSeek V4 Pro 0813 \((\w+)\)$"
    ),
    GoModelSpec("hy3", "Hy3", "Tencent", r"^Hy3$"),
    GoModelSpec("hy4-preview", "Hy4 preview", "Tencent", None),
    GoModelSpec("grok-4.7", "Grok 4.7", "SpaceXAI", r"^Grok 4\.7 \((\w+)\)$"),
    GoModelSpec("gpt-6-luna", "GPT 6 Luna", "OpenAI", r"^GPT-6 Luna \((\w+)\)$"),
    GoModelSpec("gpt-5.6-luna", "GPT 5.6 Luna", "OpenAI", r"^GPT-5\.6 Luna"),
    GoModelSpec("gemini-4-argon", "Gemini 4 Argon", "Google", r"^Gemini 4 Argon \((\w+)\)$"),
)


@dataclasses.dataclass(frozen=True)
class PriceRow:
    """One row of the Go docs price table, plain plan."""

    row_name: str
    input_prices: tuple[float, ...]
    output_prices: tuple[float, ...]
    monthly_limit: float


@dataclasses.dataclass(frozen=True)
class RequestRow:
    """One row of the Go docs requests table, plain plan."""

    row_name: str
    requests_per_month: int


@dataclasses.dataclass(frozen=True)
class PromoTerms:
    """A time-limited discount: its prices and the date it ends (ISO), if the docs give one."""

    price_in: float | None
    price_out: float | None
    ends: str | None


@dataclasses.dataclass(frozen=True)
class GoPlanEntry:
    """One OpenCode Go model as the plain Go plan describes it, with its AA rows attached."""

    go_id: str
    docs_name: str
    creator: str
    monthly_limit: float | None
    price_in: float | None
    price_out: float | None
    peak_split: bool
    promo: PromoTerms | None
    requests_per_month: int | None
    variants: tuple[str, ...] | None
    aa_rows: tuple[str, ...]
    note: str | None


@dataclasses.dataclass(frozen=True)
class AaRecord:
    """One Artificial Analysis model row: index, cost per task, prices, speed and coding scores."""

    name: str
    creator: str | None
    deprecated: bool
    intelligence_index: float | None
    cost_per_task: float | None
    price_in: float | None
    price_out: float | None
    end_to_end_seconds: float | None
    terminal_bench_4: float | None
    frontiercode_2: float | None


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "refresh-agent-models/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read().decode("utf-8", errors="replace")
    except OSError as error:
        raise SnapshotSourceError(f"could not fetch {url}") from error


def run_opencode_catalog() -> str:
    try:
        completed = subprocess.run(
            OPENCODE_CATALOG_COMMAND, capture_output=True, text=True, check=True, timeout=120
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise SnapshotSourceError(
            "could not run the OpenCode CLI; pass --opencode-models FILE"
        ) from error
    return completed.stdout


def html_to_text(raw_html: str) -> str:
    without_scripts = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", raw_html)
    with_line_breaks = re.sub(r"(?i)<br\s*/?>|</(p|div|tr|li|h[1-6]|table)>", "\n", without_scripts)
    with_cell_bars = re.sub(r"(?i)</t[dh]>", " | ", with_line_breaks)
    text = html.unescape(re.sub(r"<[^>]+>", "", with_cell_bars))
    text = re.sub(r"[ \t]+", " ", text)
    return re.sub(r"\n\s*\n+", "\n", text)


def base_name(row_name: str) -> str:
    """'DeepSeek V4.1 Flash (Peak)' -> 'DeepSeek V4.1 Flash'."""
    return re.sub(r"\s*\(.*\)\s*$", "", row_name).strip()


def parse_dollar_amounts(cell: str) -> tuple[float, ...]:
    return tuple(float(match.group(1).replace(",", "")) for match in DOLLAR_AMOUNT.finditer(cell))


def find_line_index(lines: Sequence[str], prefix: str) -> int:
    for line_index, line in enumerate(lines):
        if line.startswith(prefix):
            return line_index
    raise SnapshotSourceError(f"the Go docs page has no line starting with {prefix!r}")


def table_rows(lines: Sequence[str], header_prefix: str) -> list[list[str]]:
    """Cells of each row under the first table whose header starts with header_prefix."""
    header_index = find_line_index(lines, header_prefix)
    rows: list[list[str]] = []
    for line in lines[header_index + 1 :]:
        if line.startswith("Model |"):
            break
        cells = [cell.strip() for cell in line.split("|") if cell.strip()]
        if cells:
            rows.append(cells)
    return rows


def parse_price_rows(lines: Sequence[str]) -> list[PriceRow]:
    """Price rows of the first table after 'Usage limits': the plain Go plan."""
    usage_index = find_line_index(lines, "Usage limits")
    rows: list[PriceRow] = []
    for cells in table_rows(lines[usage_index:], "Model | Input | Output"):
        limits = parse_dollar_amounts(cells[-1]) if len(cells) >= 6 else ()
        if not limits:
            continue
        rows.append(
            PriceRow(
                row_name=cells[0],
                input_prices=parse_dollar_amounts(cells[1]),
                output_prices=parse_dollar_amounts(cells[2]),
                monthly_limit=limits[0],
            )
        )
    return rows


def parse_request_rows(lines: Sequence[str]) -> list[RequestRow]:
    """Request rows of the first table under 'Estimated requests': the plain Go plan."""
    estimate_index = find_line_index(lines, "Estimated requests")
    rows: list[RequestRow] = []
    for cells in table_rows(lines[estimate_index:], "Model | Requests per 5 hours"):
        if len(cells) >= 4 and re.fullmatch(r"[\d,]+", cells[3]):
            rows.append(
                RequestRow(row_name=cells[0], requests_per_month=int(cells[3].replace(",", "")))
            )
    return rows


def promo_end_dates(text: str) -> dict[str, str]:
    """Promo end dates keyed by base model name, from sentences like 'Gemini 4 Argon: The 50% ...'."""
    ends: dict[str, str] = {}
    for sentence in PROMO_END_SENTENCE.finditer(text):
        end_date = datetime.datetime.strptime(sentence.group(2), "%B %d, %Y").date()
        ends[base_name(sentence.group(1).strip())] = end_date.isoformat()
    return ends


def build_go_entry(
    spec: GoModelSpec,
    price_rows: Sequence[PriceRow],
    request_rows: Sequence[RequestRow],
    promo_ends: Mapping[str, str],
) -> GoPlanEntry:
    requests = [
        row.requests_per_month for row in request_rows if base_name(row.row_name) == spec.docs_name
    ]
    requests_per_month = requests[0] if requests else None
    matching = [row for row in price_rows if base_name(row.row_name) == spec.docs_name]
    if not matching:
        return GoPlanEntry(
            go_id=spec.go_id,
            docs_name=spec.docs_name,
            creator=spec.creator,
            monthly_limit=None,
            price_in=None,
            price_out=None,
            peak_split=False,
            promo=None,
            requests_per_month=requests_per_month,
            variants=None,
            aa_rows=(),
            note="not in the Go docs",
        )
    peak_rows = [row for row in matching if "(Peak)" in row.row_name]
    chosen = peak_rows[0] if peak_rows else matching[0]
    promo = None
    if len(chosen.output_prices) > 1:
        promo = PromoTerms(
            price_in=chosen.input_prices[1] if len(chosen.input_prices) > 1 else None,
            price_out=chosen.output_prices[1],
            ends=promo_ends.get(spec.docs_name),
        )
    return GoPlanEntry(
        go_id=spec.go_id,
        docs_name=spec.docs_name,
        creator=spec.creator,
        monthly_limit=chosen.monthly_limit,
        price_in=chosen.input_prices[0] if chosen.input_prices else None,
        price_out=chosen.output_prices[0] if chosen.output_prices else None,
        peak_split=bool(peak_rows),
        promo=promo,
        requests_per_month=requests_per_month,
        variants=None,
        aa_rows=(),
        note=None,
    )


def build_go_entries(text: str) -> list[GoPlanEntry]:
    lines = [line.strip() for line in text.split("\n")]
    price_rows = parse_price_rows(lines)
    request_rows = parse_request_rows(lines)
    ends = promo_end_dates(text)
    return [build_go_entry(spec, price_rows, request_rows, ends) for spec in GO_MODEL_SPECS]


def parse_aa_objects(raw: str) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    """Model rows (with index and prices) and minimal references, both keyed by name."""
    text = raw.replace('\\"', '"').replace("\\\\", "\\")
    decoder = json.JSONDecoder()
    rows: dict[str, dict[str, Any]] = {}
    minimal: dict[str, dict[str, Any]] = {}
    position = 0
    while True:
        start = text.find(MODEL_OBJECT_START, position)
        if start < 0:
            break
        try:
            parsed, position = decoder.raw_decode(text, start)
        except ValueError:
            position = start + 1
            continue
        if not isinstance(parsed, dict) or "name" not in parsed:
            continue
        if "intelligenceIndex" in parsed and "shortName" in parsed:
            rows.setdefault(parsed["shortName"], parsed)
        else:
            minimal.setdefault(parsed["name"], parsed)
    if not rows:
        raise SnapshotSourceError("the AA page has no model rows; its layout may have changed")
    return rows, minimal


def aa_record_from_object(obj: Mapping[str, Any]) -> AaRecord:
    scores = obj.get("scores") or {}
    return AaRecord(
        name=obj.get("shortName") or obj.get("name", ""),
        creator=obj.get("modelCreatorName"),
        deprecated=bool(obj.get("deprecated")),
        intelligence_index=obj.get("intelligenceIndex"),
        cost_per_task=obj.get("intelligenceIndexCostPerTask"),
        price_in=obj.get("price1mInputTokens"),
        price_out=obj.get("price1mOutputTokens"),
        end_to_end_seconds=obj.get("medianEndToEndResponseTimeSeconds"),
        terminal_bench_4=scores.get("terminalbench-4-0"),
        frontiercode_2=scores.get("frontiercode-2-0"),
    )


def attach_aa_rows(
    entries: Sequence[GoPlanEntry],
    specs: Sequence[GoModelSpec],
    rows: Mapping[str, Mapping[str, Any]],
    minimal: Mapping[str, Mapping[str, Any]],
) -> list[GoPlanEntry]:
    """Match each Go model to its AA names. A model with no index says why (deprecated or not)."""
    all_names = list(rows) + [name for name in minimal if name not in rows]
    attached: list[GoPlanEntry] = []
    for entry, spec in zip(entries, specs):
        names: tuple[str, ...] = ()
        if spec.aa_name_pattern is not None:
            names = tuple(name for name in all_names if re.search(spec.aa_name_pattern, name))
        if not names:
            attached.append(dataclasses.replace(entry, note=entry.note or "no AA row"))
            continue
        indexed_names = tuple(
            name for name in names if rows.get(name, {}).get("intelligenceIndex") is not None
        )
        if indexed_names:
            attached.append(dataclasses.replace(entry, aa_rows=indexed_names))
            continue
        deprecated = any(
            bool((rows.get(name) or minimal.get(name) or {}).get("deprecated")) for name in names
        )
        note = "deprecated on AA, no index" if deprecated else "no AA index"
        attached.append(dataclasses.replace(entry, aa_rows=names, note=note))
    return attached


def collect_aa_records(
    rows: Mapping[str, Mapping[str, Any]],
    minimal: Mapping[str, Mapping[str, Any]],
    wanted_names: frozenset[str],
) -> list[AaRecord]:
    """Every Anthropic row, plus the rows matched to a Go model."""
    records: dict[str, AaRecord] = {}
    for name, obj in rows.items():
        if obj.get("modelCreatorName") == "Anthropic" or name in wanted_names:
            records[name] = aa_record_from_object(obj)
    for name in sorted(wanted_names):
        if name not in records and name in minimal:
            records[name] = aa_record_from_object(minimal[name])
    return list(records.values())


def parse_opencode_variants(catalog_text: str) -> dict[str, tuple[str, ...]]:
    """Effort variant keys per model id, from `opencode models opencode-go --verbose`."""
    decoder = json.JSONDecoder()
    variants: dict[str, tuple[str, ...]] = {}
    for header in CATALOG_MODEL_LINE.finditer(catalog_text):
        start = catalog_text.find("{", header.end())
        if start < 0:
            continue
        try:
            parsed, _ = decoder.raw_decode(catalog_text, start)
        except ValueError:
            logger.warning("could not parse the catalog entry for %s", header.group(1))
            continue
        variants[header.group(1)] = tuple((parsed.get("variants") or {}).keys())
    if not variants:
        raise SnapshotSourceError("the OpenCode catalog lists no opencode-go models")
    return variants


def build_snapshot(aa_raw: str, go_raw: str, catalog_text: str, as_of: str) -> dict[str, Any]:
    rows, minimal = parse_aa_objects(aa_raw)
    variants = parse_opencode_variants(catalog_text)
    entries = build_go_entries(html_to_text(go_raw))
    entries = [dataclasses.replace(entry, variants=variants.get(entry.go_id)) for entry in entries]
    entries = attach_aa_rows(entries, GO_MODEL_SPECS, rows, minimal)
    wanted = frozenset(name for entry in entries for name in entry.aa_rows)
    records = collect_aa_records(rows, minimal, wanted)
    return {
        "as_of": as_of,
        "sources": {
            "aa": AA_LEADERBOARD_URL,
            "go": GO_DOCS_URL,
            "opencode_catalog": " ".join(OPENCODE_CATALOG_COMMAND),
        },
        "go": [dataclasses.asdict(entry) for entry in entries],
        "aa": [dataclasses.asdict(record) for record in records],
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Snapshot the AA leaderboard, the Go plan and the OpenCode variants."
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--fetch", action="store_true", help="download both pages")
    source.add_argument("--aa-html", type=Path, help="saved AA leaderboard page (needs --go-html)")
    parser.add_argument("--go-html", type=Path, help="saved OpenCode Go docs page")
    parser.add_argument(
        "--opencode-models",
        type=Path,
        help="saved output of `opencode models opencode-go --verbose` (default: run it)",
    )
    parser.add_argument(
        "--as-of",
        default=datetime.date.today().isoformat(),
        help="date the snapshot is for, YYYY-MM-DD",
    )
    parser.add_argument("--out", type=Path, required=True, help="where to write the snapshot JSON")
    arguments = parser.parse_args(argv)
    datetime.date.fromisoformat(arguments.as_of)
    if arguments.fetch:
        aa_raw, go_raw = fetch_text(AA_LEADERBOARD_URL), fetch_text(GO_DOCS_URL)
    else:
        if arguments.go_html is None:
            parser.error("--aa-html needs --go-html")
        aa_raw = arguments.aa_html.read_text(encoding="utf-8", errors="replace")
        go_raw = arguments.go_html.read_text(encoding="utf-8", errors="replace")
    if arguments.opencode_models is not None:
        catalog_text = arguments.opencode_models.read_text(encoding="utf-8", errors="replace")
    else:
        catalog_text = run_opencode_catalog()
    snapshot = build_snapshot(aa_raw, go_raw, catalog_text, arguments.as_of)
    arguments.out.write_text(
        json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(
        f"wrote {arguments.out}: {len(snapshot['go'])} Go models, {len(snapshot['aa'])} AA records"
    )
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())
