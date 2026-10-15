"""The calculation, tested against numbers worked out BY HAND (see docs/HOW_IT_WORKS.md), not against the code's own output."""

from __future__ import annotations

from pathlib import Path

import pytest

from pra import Counts, evaluate, parse_ground_truth, parse_nlp, read_text

DATA = Path(__file__).resolve().parent.parent / "data"


@pytest.fixture(scope="module")
def sample():
    return (
        parse_ground_truth(read_text(DATA / "ground_truth.csv")),
        parse_nlp(read_text(DATA / "nlp_output.csv")),
    )


# ------------------------------------------------------------------------------------------------ the formulas themselves

def test_counts_formulas():
    c = Counts(tp=8, fp=5, fn=7)
    assert c.precision == pytest.approx(8 / 13)
    assert c.recall == pytest.approx(8 / 15)
    assert c.f1 == pytest.approx(16 / 28)          # 2TP / (2TP + FP + FN)
    assert c.accuracy == pytest.approx(8 / 20)     # TP / (TP + FP + FN)


def test_undefined_metrics_are_none_not_zero_or_one():
    nothing_predicted = Counts(tp=0, fp=0, fn=2)
    assert nothing_predicted.precision is None
    assert nothing_predicted.recall == 0.0
    nothing_expected = Counts(tp=0, fp=1, fn=0)
    assert nothing_expected.recall is None
    assert nothing_expected.precision == 0.0
    assert Counts().f1 is None and Counts().accuracy is None


# ------------------------------------------------------------------------------------------------ the worked example

def test_headline_numbers_of_the_worked_example(sample):
    result = evaluate(*sample)

    d = result.delivered.counts
    assert (d.tp, d.fp, d.fn) == (8, 5, 7)
    assert d.precision == pytest.approx(8 / 13)     # 61.5 %
    assert d.recall == pytest.approx(8 / 15)        # 53.3 %

    r = result.raw.counts
    assert (r.tp, r.fp, r.fn) == (11, 7, 4)
    assert r.recall == pytest.approx(11 / 15)       # 73.3 %


def test_per_chart_results_of_the_worked_example(sample):
    by_id = {c.chart_id: c for c in evaluate(*sample).charts}

    one = by_id["CH-1001"]
    assert one.tp_codes == ("E11.9", "I10")
    assert one.fp_codes == ("E11.65",)
    assert one.wrongly_suppressed == ("E78.5", "Z79.4")
    assert one.correctly_suppressed == ("E11.40",)
    assert one.missed == ()

    three = by_id["CH-1003"]
    assert three.wrongly_suppressed == ("G47.33",)     # NLP had it and threw it away
    assert three.missed == ("E66.9",)                  # NLP never produced it
    assert three.fp_codes == ("F41.9",)

    assert by_id["CH-1004"].delivered == Counts(2, 0, 0)    # the lower-case "z79.01" matched after normalization
    assert by_id["CH-1007"].has_nlp is False                 # chart missing from the NLP output
    assert by_id["CH-1007"].delivered == Counts(0, 0, 2)


def test_suppression_analysis_of_the_worked_example(sample):
    s = evaluate(*sample).suppression
    assert s.total_suppressed == 5
    assert s.correctly_suppressed == 2          # E11.40 and I10 were right to hide
    assert s.wrongly_suppressed == 3            # E78.5, Z79.4, G47.33 were right and were hidden
    assert s.suppression_precision == pytest.approx(2 / 5)
    assert s.recall_change == pytest.approx(8 / 15 - 11 / 15)   # -3 truth codes out of 15 = -20 points


def test_macro_averages_skip_undefined_charts(sample):
    d = evaluate(*sample).delivered
    # precision is undefined for CH-1007 (nothing suggested): averaged over the other 6 charts
    assert d.macro_precision == pytest.approx((2 / 3 + 3 / 4 + 1 / 2 + 1 + 0 + 0) / 6)
    # recall is undefined for CH-1006 (no truth codes): averaged over the other 6 charts
    assert d.macro_recall == pytest.approx((1 / 2 + 1 + 1 / 3 + 1 + 0 + 0) / 6)


def test_warnings_explain_the_two_odd_charts(sample):
    text = " ".join(evaluate(*sample).warnings)
    assert "CH-1007" in text and "no NLP output" in text
    assert "CH-1006" in text and "recall is undefined" in text


# ------------------------------------------------------------------------------------------------ matching modes

def test_exact_mode_is_stricter_than_normalized(sample):
    exact = evaluate(*sample, mode="exact").delivered.counts
    normalized = evaluate(*sample, mode="normalized").delivered.counts
    assert (exact.tp, exact.fp, exact.fn) == (7, 6, 8)     # "z79.01" no longer matches "Z79.01"
    assert normalized.tp == 8


def test_category_mode_forgives_a_parent_code_for_a_child_code():
    truth = {"C1": ["M54.50"]}
    nlp = {"C1": {"suggested": ["M54.5"], "suppressed": []}}
    assert evaluate(truth, nlp, "normalized").delivered.counts == Counts(0, 1, 1)
    assert evaluate(truth, nlp, "icd10_category").delivered.counts == Counts(1, 0, 0)


# ------------------------------------------------------------------------------------------------ edge cases

def test_duplicate_codes_in_a_chart_count_once():
    truth = {"C1": ["E11.9", "E11.9", "e119"]}
    nlp = {"C1": {"suggested": ["E11.9", "E11.9"], "suppressed": []}}
    assert evaluate(truth, nlp).delivered.counts == Counts(1, 0, 0)


def test_code_both_suggested_and_suppressed_counts_as_suggested_and_warns():
    truth = {"C1": ["I10"]}
    nlp = {"C1": {"suggested": ["I10"], "suppressed": ["I10"]}}
    result = evaluate(truth, nlp)
    assert result.delivered.counts == Counts(1, 0, 0)
    assert result.charts[0].wrongly_suppressed == ()
    assert any("BOTH suggested and suppressed" in w for w in result.warnings)


def test_charts_without_ground_truth_are_not_scored_and_are_reported():
    result = evaluate({"A": ["I10"]}, {"A": {"suggested": ["I10"], "suppressed": []}, "B": {"suggested": ["E11.9"], "suppressed": []}})
    assert [c.chart_id for c in result.charts] == ["A"]
    assert any("B" in w and "NOT scored" in w for w in result.warnings)


def test_per_code_statistics_find_the_codes_that_cause_errors(sample):
    stats = {s.code: s for s in evaluate(*sample).by_code}
    assert stats["E11.9"].tp == 1 and stats["E11.9"].missed == 1       # found in CH-1001, missed in CH-1007
    assert stats["I10"].tp == 1 and stats["I10"].correctly_suppressed == 1
    assert stats["E78.5"].wrongly_suppressed == 1
    assert evaluate(*sample).by_code[0].errors >= evaluate(*sample).by_code[-1].errors     # sorted worst first


def test_everything_perfect_and_everything_empty():
    perfect = evaluate({"A": ["I10"]}, {"A": {"suggested": ["I10"], "suppressed": []}})
    assert perfect.delivered.counts.precision == 1.0 and perfect.delivered.counts.recall == 1.0
    empty = evaluate({"A": []}, {"A": {"suggested": [], "suppressed": []}})
    assert empty.delivered.counts.precision is None and empty.delivered.counts.recall is None
