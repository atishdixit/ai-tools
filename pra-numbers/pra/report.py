"""Plain-text report (used by the command line)."""

from __future__ import annotations

from .metrics import EvaluationResult
from .models import Summary
from .normalize import MODE_LABELS


def pct(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:.1f}%"


def _summary_lines(title: str, summary: Summary) -> list[str]:
    c = summary.counts
    return [
        title,
        f"  codes: TP={c.tp}  FP={c.fp}  FN={c.fn}",
        f"  pooled (micro):   precision={pct(c.precision)}  recall={pct(c.recall)}  F1={pct(c.f1)}  accuracy={pct(c.accuracy)}",
        f"  per-chart (macro): precision={pct(summary.macro_precision)}  recall={pct(summary.macro_recall)}  "
        f"F1={pct(summary.macro_f1)}  accuracy={pct(summary.macro_accuracy)}",
    ]


def text_report(result: EvaluationResult) -> str:
    lines = [f"Matching: {MODE_LABELS[result.mode]}", f"Charts scored: {len(result.charts)}", ""]
    lines += _summary_lines("DELIVERED to the coder (suggested codes only)  <- headline numbers", result.delivered)
    lines.append("")
    lines += _summary_lines("RAW NLP (suggested + suppressed)", result.raw)
    s = result.suppression
    lines += [
        "",
        "SUPPRESSION",
        f"  suppressed codes: {s.total_suppressed}  (correctly hidden: {s.correctly_suppressed}, wrongly hidden: {s.wrongly_suppressed})",
        f"  suppression precision: {pct(s.suppression_precision)}",
        f"  effect on recall: {_signed(s.recall_change)}   effect on precision: {_signed(s.precision_change)}",
        "",
        "PER CHART (delivered view)",
        f"  {'chart':<10} {'TP':>3} {'FP':>3} {'FN':>3}  {'precision':>9} {'recall':>8} {'F1':>7}   notes",
    ]
    for chart in result.charts:
        d = chart.delivered
        notes = []
        if chart.fp_codes:
            notes.append("FP: " + ", ".join(chart.fp_codes))
        if chart.wrongly_suppressed:
            notes.append("wrongly suppressed: " + ", ".join(chart.wrongly_suppressed))
        if chart.missed:
            notes.append("missed: " + ", ".join(chart.missed))
        lines.append(f"  {chart.chart_id:<10} {d.tp:>3} {d.fp:>3} {d.fn:>3}  {pct(d.precision):>9} {pct(d.recall):>8} {pct(d.f1):>7}   "
                     + "; ".join(notes))
    if result.warnings:
        lines += ["", "WARNINGS"] + [f"  - {w}" for w in result.warnings]
    return "\n".join(lines)


def _signed(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:+.1f} points"
