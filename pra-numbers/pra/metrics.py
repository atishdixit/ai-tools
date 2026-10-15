"""The PRA calculation: compare NLP predictions with the ground truth, chart by chart.

Strategy in one paragraph
-------------------------
The ground truth (Visionary) decides what is right. For every chart we turn each side into a *set* of codes and compare the sets.
The NLP output has two lists per chart. Only **suggested** codes are what the coder is actually shown, so they are the system's
predictions (the *delivered* view, the headline numbers). **Suppressed** codes were found but hidden: they are not predictions, but
they are the key to understanding recall: a truth code that is suppressed is a code the system had and threw away. We therefore also
report a *raw* view (suggested + suppressed) and a suppression analysis. See docs/HOW_IT_WORKS.md for worked examples.
"""

from __future__ import annotations

from collections import defaultdict
from typing import Iterable, Mapping

from .models import ChartEvaluation, CodeStats, Counts, EvaluationResult, Summary, SuppressionSummary
from .normalize import DEFAULT_MODE, normalize_code

SUGGESTED = "suggested"
SUPPRESSED = "suppressed"


def _keyed(codes: Iterable[str], mode: str, spelling: dict[str, str]) -> set[str]:
    """Normalize codes to comparison keys, remembering one readable spelling per key (the first one seen)."""
    keys: set[str] = set()
    for code in codes:
        text = str(code).strip()
        if not text:
            continue
        key = normalize_code(text, mode)
        if not key:
            continue
        keys.add(key)
        spelling.setdefault(key, text)
    return keys


def _evaluate_chart(chart_id: str, truth: Iterable[str] | None, nlp: Mapping[str, Iterable[str]] | None, mode: str) -> ChartEvaluation:
    spelling: dict[str, str] = {}
    # truth first, so that the displayed spelling of a matched code is the ground truth's spelling
    truth_keys = _keyed(truth or (), mode, spelling)
    suggested_keys = _keyed((nlp or {}).get(SUGGESTED, ()), mode, spelling)
    suppressed_all = _keyed((nlp or {}).get(SUPPRESSED, ()), mode, spelling)

    conflicting = suggested_keys & suppressed_all
    suppressed_keys = suppressed_all - suggested_keys   # a code that is also suggested was delivered: suggested wins

    tp = truth_keys & suggested_keys
    fp = suggested_keys - truth_keys
    fn = truth_keys - suggested_keys
    wrongly_suppressed = truth_keys & suppressed_keys
    missed = truth_keys - suggested_keys - suppressed_keys
    correctly_suppressed = suppressed_keys - truth_keys

    raw_predicted = suggested_keys | suppressed_keys
    raw = Counts(
        tp=len(truth_keys & raw_predicted),
        fp=len(raw_predicted - truth_keys),
        fn=len(truth_keys - raw_predicted),
    )

    def show(keys: set[str]) -> tuple[str, ...]:
        return tuple(sorted(spelling[k] for k in keys))

    return ChartEvaluation(
        chart_id=chart_id,
        has_truth=truth is not None,
        has_nlp=nlp is not None,
        truth=show(truth_keys),
        suggested=show(suggested_keys),
        suppressed=show(suppressed_keys),
        tp_codes=show(tp),
        fp_codes=show(fp),
        fn_codes=show(fn),
        wrongly_suppressed=show(wrongly_suppressed),
        missed=show(missed),
        correctly_suppressed=show(correctly_suppressed),
        conflicting=show(conflicting),
        delivered=Counts(len(tp), len(fp), len(fn)),
        raw=raw,
    )


def _mean(values: Iterable[float | None]) -> float | None:
    defined = [v for v in values if v is not None]
    return None if not defined else sum(defined) / len(defined)


def _summarise(counts_per_chart: list[Counts]) -> Summary:
    pooled = Counts()
    for counts in counts_per_chart:
        pooled = pooled + counts
    return Summary(
        counts=pooled,
        macro_precision=_mean(c.precision for c in counts_per_chart),
        macro_recall=_mean(c.recall for c in counts_per_chart),
        macro_f1=_mean(c.f1 for c in counts_per_chart),
        macro_accuracy=_mean(c.accuracy for c in counts_per_chart),
    )


def _difference(a: float | None, b: float | None) -> float | None:
    return None if a is None or b is None else a - b


def evaluate(
    truth: Mapping[str, Iterable[str]],
    nlp: Mapping[str, Mapping[str, Iterable[str]]],
    mode: str = DEFAULT_MODE,
) -> EvaluationResult:
    """Score the NLP output against the ground truth.

    :param truth: ``{chart_id: [codes]}``: the master of truth. Only charts listed here are scored.
    :param nlp: ``{chart_id: {"suggested": [codes], "suppressed": [codes]}}``
    :param mode: how strictly codes are matched, see :mod:`pra.normalize`.
    """
    warnings: list[str] = []

    unmatched = sorted(set(nlp) - set(truth))
    if unmatched:
        warnings.append(
            f"{len(unmatched)} chart(s) have NLP output but no ground truth and were NOT scored: {', '.join(unmatched)}. "
            "Without a master of truth there is nothing to compare against."
        )

    charts = [
        _evaluate_chart(chart_id, truth[chart_id], nlp.get(chart_id), mode)
        for chart_id in sorted(truth)
    ]

    no_nlp = [c.chart_id for c in charts if not c.has_nlp]
    if no_nlp:
        warnings.append(
            f"{len(no_nlp)} scored chart(s) have no NLP output at all and are treated as 'nothing suggested' "
            f"(every truth code counts as missed): {', '.join(no_nlp)}."
        )
    no_truth = [c.chart_id for c in charts if not c.truth]
    if no_truth:
        warnings.append(
            f"{len(no_truth)} chart(s) have no ground-truth codes, so recall is undefined for them (any suggestion there is a false "
            f"positive): {', '.join(no_truth)}."
        )
    for chart in charts:
        if chart.conflicting:
            warnings.append(
                f"{chart.chart_id}: code(s) {', '.join(chart.conflicting)} appear as BOTH suggested and suppressed. "
                "They were treated as suggested (delivered)."
            )

    delivered = _summarise([c.delivered for c in charts])
    raw = _summarise([c.raw for c in charts])

    total_suppressed = sum(len(c.suppressed) for c in charts)
    wrong = sum(len(c.wrongly_suppressed) for c in charts)
    correct = sum(len(c.correctly_suppressed) for c in charts)
    suppression = SuppressionSummary(
        total_suppressed=total_suppressed,
        correctly_suppressed=correct,
        wrongly_suppressed=wrong,
        suppression_precision=None if total_suppressed == 0 else correct / total_suppressed,
        recall_change=_difference(delivered.counts.recall, raw.counts.recall),
        precision_change=_difference(delivered.counts.precision, raw.counts.precision),
    )

    return EvaluationResult(
        mode=mode,
        charts=tuple(charts),
        delivered=delivered,
        raw=raw,
        suppression=suppression,
        by_code=_by_code(charts),
        warnings=tuple(warnings),
    )


def _by_code(charts: list[ChartEvaluation]) -> tuple[CodeStats, ...]:
    tally: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for chart in charts:
        for code in chart.tp_codes:
            tally[code]["tp"] += 1
        for code in chart.fp_codes:
            tally[code]["fp"] += 1
        for code in chart.wrongly_suppressed:
            tally[code]["wrongly_suppressed"] += 1
        for code in chart.missed:
            tally[code]["missed"] += 1
        for code in chart.correctly_suppressed:
            tally[code]["correctly_suppressed"] += 1
    stats = [CodeStats(code=code, **counts) for code, counts in tally.items()]
    return tuple(sorted(stats, key=lambda s: (-s.errors, -s.tp, s.code)))
