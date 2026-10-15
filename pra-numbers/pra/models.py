"""Data structures for the PRA calculation. No third-party dependencies."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Counts:
    """The three numbers every metric is built from, for one chart or for the whole data set.

    ``tp`` true positives: codes the system delivered that the ground truth also has.
    ``fp`` false positives: codes the system delivered that the ground truth does NOT have.
    ``fn`` false negatives: ground-truth codes the system did not deliver.

    A metric is ``None`` ("undefined") when its denominator is zero, e.g. precision for a chart where the system delivered nothing.
    ``None`` is deliberate: reporting 0% or 100% there would be inventing a number.
    """

    tp: int = 0
    fp: int = 0
    fn: int = 0

    @property
    def predicted(self) -> int:
        return self.tp + self.fp

    @property
    def actual(self) -> int:
        return self.tp + self.fn

    @property
    def precision(self) -> float | None:
        """Of the codes delivered, how many were right?  TP / (TP + FP)."""
        return None if self.predicted == 0 else self.tp / self.predicted

    @property
    def recall(self) -> float | None:
        """Of the codes that should have been found, how many were?  TP / (TP + FN)."""
        return None if self.actual == 0 else self.tp / self.actual

    @property
    def f1(self) -> float | None:
        """Single balance of precision and recall.  2TP / (2TP + FP + FN).  Defined whenever anything exists at all."""
        denominator = 2 * self.tp + self.fp + self.fn
        return None if denominator == 0 else 2 * self.tp / denominator

    @property
    def accuracy(self) -> float | None:
        """Set accuracy (Jaccard overlap).  TP / (TP + FP + FN).  There are no 'true negatives' for code sets, so classic accuracy
        does not exist; this is the usual stand-in: the share of all codes involved that were agreed on."""
        denominator = self.tp + self.fp + self.fn
        return None if denominator == 0 else self.tp / denominator

    def __add__(self, other: "Counts") -> "Counts":
        return Counts(self.tp + other.tp, self.fp + other.fp, self.fn + other.fn)


@dataclass(frozen=True)
class ChartEvaluation:
    """Everything known about one chart, ready to display. All code lists are sorted and use the original spelling."""

    chart_id: str
    has_truth: bool            # the chart is in the ground-truth file
    has_nlp: bool              # the chart appears in the NLP output (False = NLP returned nothing at all for it)

    truth: tuple[str, ...]
    suggested: tuple[str, ...]
    suppressed: tuple[str, ...]

    # --- delivered view: only suggested codes count as predictions (what the coder is actually shown)
    tp_codes: tuple[str, ...]
    fp_codes: tuple[str, ...]
    fn_codes: tuple[str, ...]
    # why a truth code was not delivered
    wrongly_suppressed: tuple[str, ...]   # NLP found it but suppressed it
    missed: tuple[str, ...]               # NLP never produced it (neither suggested nor suppressed)
    # suppressed codes that are NOT in the truth: suppression did the right thing
    correctly_suppressed: tuple[str, ...]
    # codes listed as both suggested and suppressed in the input (treated as suggested)
    conflicting: tuple[str, ...]

    delivered: Counts
    raw: Counts                # raw view: suggested + suppressed both count as predictions


@dataclass(frozen=True)
class Summary:
    """Pooled (micro) counts plus the per-chart averages (macro) for one view."""

    counts: Counts
    macro_precision: float | None
    macro_recall: float | None
    macro_f1: float | None
    macro_accuracy: float | None


@dataclass(frozen=True)
class SuppressionSummary:
    """What the suppression step did to the numbers."""

    total_suppressed: int
    correctly_suppressed: int      # suppressed and NOT in the truth: a wrong code was successfully hidden
    wrongly_suppressed: int        # suppressed but IN the truth: a right code was hidden (recall loss)
    suppression_precision: float | None   # correctly_suppressed / total_suppressed: how good a filter is it?
    recall_change: float | None           # delivered recall - raw recall (negative = suppression cost recall)
    precision_change: float | None        # delivered precision - raw precision (positive = suppression helped precision)


@dataclass(frozen=True)
class CodeStats:
    """How one code behaves across all charts (delivered view)."""

    code: str
    tp: int = 0
    fp: int = 0
    wrongly_suppressed: int = 0
    missed: int = 0
    correctly_suppressed: int = 0

    @property
    def fn(self) -> int:
        return self.wrongly_suppressed + self.missed

    @property
    def errors(self) -> int:
        return self.fp + self.fn


@dataclass(frozen=True)
class EvaluationResult:
    mode: str
    charts: tuple[ChartEvaluation, ...]
    delivered: Summary
    raw: Summary
    suppression: SuppressionSummary
    by_code: tuple[CodeStats, ...]
    warnings: tuple[str, ...]
