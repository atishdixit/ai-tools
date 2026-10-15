"""Reading the two inputs: the ground truth and the NLP output.

Both are accepted as CSV (one row per chart + code, the shape you get from a database export) or JSON (one object per chart).
Every problem is reported as an :class:`InputError` with a message a person can act on, never a stack trace.
"""

from __future__ import annotations

import csv
import io
import json
from pathlib import Path
from typing import Any

SUGGESTED_WORDS = {"suggested", "suggestion", "suggest", "recommended", "recommendation"}
SUPPRESSED_WORDS = {"suppressed", "suppression", "suppress"}

_CHART_ALIASES = ("chart_id", "chartid", "chart", "chart_number", "encounter_id")
_CODE_ALIASES = ("code", "medical_code", "icd", "icd10", "icd_10", "icd10_code", "hcc_code")
_STATUS_ALIASES = ("status", "type", "action", "state")


class InputError(ValueError):
    """The uploaded data is not in a format we understand. The message says what to fix."""


def read_text(path: str | Path) -> str:
    return Path(path).read_text(encoding="utf-8-sig")


def _header_key(name: str | None) -> str:
    return (name or "").strip().lower().replace(" ", "_").replace("-", "_")


def _pick(headers: dict[str, str], aliases: tuple[str, ...]) -> str | None:
    for alias in aliases:
        if alias in headers:
            return headers[alias]
    return None


def _csv_rows(text: str, *, need_status: bool, source: str) -> list[tuple[str, str, str | None]]:
    reader = csv.DictReader(io.StringIO(text.strip("﻿")))
    if not reader.fieldnames:
        raise InputError(f"{source}: the file is empty.")
    headers = {_header_key(name): name for name in reader.fieldnames if name is not None}
    chart_col = _pick(headers, _CHART_ALIASES)
    code_col = _pick(headers, _CODE_ALIASES)
    status_col = _pick(headers, _STATUS_ALIASES)
    missing = []
    if chart_col is None:
        missing.append("chart_id")
    if code_col is None:
        missing.append("code")
    if need_status and status_col is None:
        missing.append("status (suggested / suppressed)")
    if missing:
        raise InputError(f"{source}: missing column(s): {', '.join(missing)}. Found columns: {', '.join(reader.fieldnames)}.")

    rows = []
    for line_number, row in enumerate(reader, start=2):
        chart = (row.get(chart_col) or "").strip()
        code = (row.get(code_col) or "").strip()
        status = (row.get(status_col) or "").strip() if status_col else None
        if not chart and not code:
            continue
        if not chart:
            raise InputError(f"{source}, line {line_number}: the chart_id is empty.")
        rows.append((chart, code, status))
    if not rows:
        raise InputError(f"{source}: no data rows found.")
    return rows


def _as_code_list(value: Any, where: str) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [part.strip() for part in value.replace(";", ",").split(",") if part.strip()]
    if isinstance(value, (list, tuple, set)):
        return [str(item).strip() for item in value if str(item).strip()]
    raise InputError(f"{where}: expected a list of codes, got {type(value).__name__}.")


def _json(text: str, source: str) -> Any:
    try:
        return json.loads(text)
    except json.JSONDecodeError as error:
        raise InputError(f"{source}: not valid JSON ({error.msg} at line {error.lineno}).") from error


def _looks_like_json(text: str) -> bool:
    return text.lstrip("﻿ \t\r\n")[:1] in ("{", "[")


def parse_ground_truth(text: str, source: str = "ground truth") -> dict[str, list[str]]:
    """Return ``{chart_id: [codes]}``.

    CSV: columns ``chart_id,code`` (a chart with a blank code is a chart that has no codes).
    JSON: ``{"CH-1": ["E11.9", "I10"]}`` or ``[{"chart_id": "CH-1", "codes": ["E11.9"]}]``.
    """
    truth: dict[str, list[str]] = {}
    if _looks_like_json(text):
        data = _json(text, source)
        if isinstance(data, dict):
            items = data.items()
        elif isinstance(data, list):
            try:
                items = [(str(entry["chart_id"]), entry.get("codes", entry.get("ground_truth", []))) for entry in data]
            except (KeyError, AttributeError, TypeError) as error:
                raise InputError(f"{source}: every list entry needs a 'chart_id' and a 'codes' list.") from error
        else:
            raise InputError(f"{source}: JSON must be an object {{chart_id: [codes]}} or a list of {{chart_id, codes}}.")
        for chart, codes in items:
            truth.setdefault(str(chart), []).extend(_as_code_list(codes, f"{source}, chart {chart}"))
        if not truth:
            raise InputError(f"{source}: no charts found.")
        return truth

    for chart, code, _ in _csv_rows(text, need_status=False, source=source):
        bucket = truth.setdefault(chart, [])
        if code:
            bucket.append(code)
    return truth


def _normalise_status(status: str | None, source: str, chart: str) -> str:
    word = (status or "").strip().lower()
    if word in SUGGESTED_WORDS:
        return "suggested"
    if word in SUPPRESSED_WORDS:
        return "suppressed"
    raise InputError(
        f"{source}, chart {chart}: unknown status {status!r}. Use 'suggested' or 'suppressed' "
        f"(also accepted: {', '.join(sorted(SUGGESTED_WORDS | SUPPRESSED_WORDS - {'suggested', 'suppressed'}))})."
    )


def parse_nlp(text: str, source: str = "NLP output") -> dict[str, dict[str, list[str]]]:
    """Return ``{chart_id: {"suggested": [...], "suppressed": [...]}}``.

    CSV: columns ``chart_id,code,status``. JSON: ``{"CH-1": {"suggested": [...], "suppressed": [...]}}`` or a list of
    ``{"chart_id": ..., "suggested": [...], "suppressed": [...]}``.
    """
    nlp: dict[str, dict[str, list[str]]] = {}

    def bucket(chart: str) -> dict[str, list[str]]:
        return nlp.setdefault(chart, {"suggested": [], "suppressed": []})

    if _looks_like_json(text):
        data = _json(text, source)
        if isinstance(data, dict):
            entries = [(str(chart), body) for chart, body in data.items()]
        elif isinstance(data, list):
            try:
                entries = [(str(entry["chart_id"]), entry) for entry in data]
            except (KeyError, TypeError) as error:
                raise InputError(f"{source}: every list entry needs a 'chart_id'.") from error
        else:
            raise InputError(f"{source}: JSON must be an object {{chart_id: {{suggested, suppressed}}}} or a list of them.")
        for chart, body in entries:
            if not isinstance(body, dict):
                raise InputError(f"{source}, chart {chart}: expected an object with 'suggested' and 'suppressed' lists.")
            target = bucket(chart)
            target["suggested"].extend(_as_code_list(body.get("suggested", body.get("suggestions")), f"{source}, chart {chart}"))
            target["suppressed"].extend(_as_code_list(body.get("suppressed", body.get("suppressions")), f"{source}, chart {chart}"))
        if not nlp:
            raise InputError(f"{source}: no charts found.")
        return nlp

    for chart, code, status in _csv_rows(text, need_status=True, source=source):
        target = bucket(chart)               # a row with a blank code still registers the chart ("NLP ran and found nothing")
        if code:
            target[_normalise_status(status, source, chart)].append(code)
    return nlp
