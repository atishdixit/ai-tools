"""PRA Number Calculator: a small Streamlit UI on top of the `pra` package.

Run:   streamlit run app.py        (or use run.bat / run.sh, which also set everything up)
"""

from __future__ import annotations

from pathlib import Path

import pandas as pd
import streamlit as st

from pra import DEFAULT_MODE, MODE_LABELS, MODES, InputError, evaluate, parse_ground_truth, parse_nlp, read_text
from pra.models import ChartEvaluation, EvaluationResult, Summary
from pra.report import pct

ROOT = Path(__file__).resolve().parent
SAMPLE_TRUTH = ROOT / "data" / "ground_truth.csv"
SAMPLE_NLP = ROOT / "data" / "nlp_output.csv"

st.set_page_config(page_title="PRA Number Calculator", page_icon=":bar_chart:", layout="wide")


# ------------------------------------------------------------------------------------------------------------------ helpers

def points(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:+.1f} pts"


def figures(summary: Summary, averaging: str) -> tuple[float | None, float | None, float | None, float | None]:
    """(precision, recall, f1, accuracy) for either pooled (micro) or per-chart-average (macro)."""
    if averaging == "micro":
        c = summary.counts
        return c.precision, c.recall, c.f1, c.accuracy
    return summary.macro_precision, summary.macro_recall, summary.macro_f1, summary.macro_accuracy


def decode(uploaded) -> str:
    return uploaded.getvalue().decode("utf-8-sig")


def chart_frame(result: EvaluationResult) -> pd.DataFrame:
    rows = []
    for c in result.charts:
        rows.append(
            {
                "Chart": c.chart_id,
                "Truth codes": len(c.truth),
                "Suggested": len(c.suggested),
                "Suppressed": len(c.suppressed),
                "TP": c.delivered.tp,
                "FP": c.delivered.fp,
                "FN": c.delivered.fn,
                "Precision": c.delivered.precision,
                "Recall": c.delivered.recall,
                "F1": c.delivered.f1,
                "Raw recall": c.raw.recall,
            }
        )
    return pd.DataFrame(rows)


def printable(frame: pd.DataFrame) -> pd.DataFrame:
    """Percent strings (and 'n/a' for undefined) for display."""
    shown = frame.copy()
    for column in ("Precision", "Recall", "F1", "Raw recall"):
        shown[column] = shown[column].map(lambda v: pct(None if pd.isna(v) else v))
    return shown


def verdicts(chart: ChartEvaluation) -> pd.DataFrame:
    rows = (
        [(code, "True positive", "Correct code, delivered to the coder") for code in chart.tp_codes]
        + [(code, "False positive", "Delivered, but NOT in the ground truth: hurts precision") for code in chart.fp_codes]
        + [(code, "False negative: wrongly suppressed", "In the ground truth; NLP found it but suppressed it: hurts recall") for code in chart.wrongly_suppressed]
        + [(code, "False negative: missed", "In the ground truth; NLP never produced it: hurts recall") for code in chart.missed]
        + [(code, "Correctly suppressed", "Not in the ground truth; hiding it was right") for code in chart.correctly_suppressed]
    )
    return pd.DataFrame(rows, columns=["Code", "Verdict", "Why"])


def metric_row(title: str, summary: Summary, averaging: str, compare: Summary | None = None) -> None:
    precision, recall, f1, accuracy = figures(summary, averaging)
    reference = figures(compare, averaging) if compare else (None, None, None, None)
    with st.container(border=True):
        st.markdown(f"**{title}**")
        columns = st.columns(4)
        for column, label, value, other in zip(columns, ("Precision", "Recall", "F1", "Accuracy"), (precision, recall, f1, accuracy), reference):
            delta = None if value is None or other is None else f"{(value - other) * 100:+.1f} pts"
            column.metric(label, pct(value), delta=delta)


# ------------------------------------------------------------------------------------------------------------------ sidebar

st.title("PRA Number Calculator")
st.caption("Precision, Recall and Accuracy of NLP-predicted medical codes, measured against the Visionary ground truth.")

with st.sidebar:
    st.header("1. Data")
    source = st.radio("Which data?", ["Sample data", "Upload my own files"], help="The sample is a small, hand-checked example (7 charts).")
    truth_text = nlp_text = None
    if source == "Sample data":
        truth_text, nlp_text = read_text(SAMPLE_TRUTH), read_text(SAMPLE_NLP)
    else:
        truth_file = st.file_uploader("Ground truth (Visionary codes)", type=["csv", "json", "txt"], key="truth")
        nlp_file = st.file_uploader("NLP output (suggested / suppressed)", type=["csv", "json", "txt"], key="nlp")
        if truth_file and nlp_file:
            truth_text, nlp_text = decode(truth_file), decode(nlp_file)

    st.header("2. How strictly to match codes")
    mode = st.selectbox("Code matching", MODES, index=MODES.index(DEFAULT_MODE), format_func=MODE_LABELS.get)

    st.header("3. Averaging")
    averaging = st.radio(
        "Headline numbers",
        ["micro", "macro"],
        format_func=lambda v: "Micro: pool every code of every chart" if v == "micro" else "Macro: average of the per-chart values",
        help="Micro weights big charts more. Macro treats every chart equally.",
    )

    with st.expander("Input file formats"):
        st.markdown(
            "**Ground truth CSV** `chart_id,code`: one row per code. A chart with no codes: a row with the code left blank.\n\n"
            "**NLP CSV** `chart_id,code,status` where status is `suggested` or `suppressed`.\n\n"
            "**JSON** works too: `{\"CH-1\": [\"E11.9\"]}` and `{\"CH-1\": {\"suggested\": [], \"suppressed\": []}}`."
        )

if truth_text is None or nlp_text is None:
    st.info("Upload both files in the sidebar, or switch to **Sample data** to see how it works.")
    st.stop()

try:
    truth = parse_ground_truth(truth_text, source="ground truth file")
    nlp = parse_nlp(nlp_text, source="NLP output file")
except InputError as error:
    st.error(str(error))
    st.stop()

result = evaluate(truth, nlp, mode)
frame = chart_frame(result)

# ------------------------------------------------------------------------------------------------------------------ headline

st.subheader("Headline numbers")
metric_row("Delivered to the coder (suggested codes only): the headline", result.delivered, averaging, compare=result.raw)
metric_row("Raw NLP (suggested + suppressed): what the model found before suppression", result.raw, averaging)

counts = result.delivered.counts
st.caption(
    f"{len(result.charts)} charts scored.  Delivered view: **{counts.tp}** correct, **{counts.fp}** wrong, **{counts.fn}** missed.  "
    "The delivered view is the headline: it is what the coder actually sees. "
    "The small arrows under it show the change compared with the raw view (what suppression did)."
)

s = result.suppression
if s.total_suppressed == 0:
    st.info("Nothing was suppressed, so the delivered and raw views are identical.")
else:
    st.info(
        f"**Suppression:** {s.total_suppressed} codes were suppressed. {s.correctly_suppressed} were wrong codes that were rightly hidden; "
        f"**{s.wrongly_suppressed} were correct codes that were hidden** (they count as missed). "
        f"Effect: recall {points(s.recall_change)}, precision {points(s.precision_change)}."
    )

# ------------------------------------------------------------------------------------------------------------------ tabs

tab_chart, tab_suppression, tab_code, tab_data, tab_how = st.tabs(
    ["Per chart", "Suppression", "By code", "Warnings and data", "How it works"]
)

with tab_chart:
    st.dataframe(printable(frame), hide_index=True)
    st.download_button("Download per-chart results (CSV)", frame.to_csv(index=False), file_name="pra_per_chart.csv", mime="text/csv")

    st.markdown("**Precision and recall by chart** (no bar means 0% or undefined: see the table above)")
    st.bar_chart(frame.set_index("Chart")[["Precision", "Recall"]], stack=False)  # grouped, not stacked: two ratios must not add up

    st.markdown("---")
    chosen = st.selectbox("Inspect one chart", [c.chart_id for c in result.charts])
    chart = next(c for c in result.charts if c.chart_id == chosen)
    d = chart.delivered
    a, b, c3 = st.columns(3)
    a.markdown("**Ground truth (Visionary)**  \n" + (", ".join(chart.truth) or "_no codes_"))
    b.markdown("**NLP suggested**  \n" + (", ".join(chart.suggested) or "_nothing_"))
    c3.markdown("**NLP suppressed**  \n" + (", ".join(chart.suppressed) or "_nothing_"))
    detail = verdicts(chart)
    if detail.empty:
        st.write("Nothing to classify for this chart.")
    else:
        st.dataframe(detail, hide_index=True)
    st.code(
        f"Precision = TP / (TP + FP) = {d.tp} / {d.predicted} = {pct(d.precision)}\n"
        f"Recall    = TP / (TP + FN) = {d.tp} / {d.actual} = {pct(d.recall)}\n"
        f"F1        = 2TP / (2TP + FP + FN) = {2 * d.tp} / {2 * d.tp + d.fp + d.fn} = {pct(d.f1)}",
        language="text",
    )

with tab_suppression:
    st.markdown(
        "Suppressed codes are codes the NLP **found but chose not to show**. They are not predictions, so they do not count as "
        "true or false positives. They matter because a suppressed code that is in the ground truth is a **lost** code."
    )
    cols = st.columns(4)
    cols[0].metric("Suppressed codes", s.total_suppressed)
    cols[1].metric("Rightly suppressed", s.correctly_suppressed, help="Not in the ground truth: hiding them removed a would-be false positive")
    cols[2].metric("Wrongly suppressed", s.wrongly_suppressed, help="In the ground truth: hiding them cost recall")
    cols[3].metric("Suppression precision", pct(s.suppression_precision), help="Rightly suppressed / all suppressed. Low = the filter throws away good codes")
    rows = []
    for c in result.charts:
        rows += [(c.chart_id, code, "Wrongly suppressed (code was correct)") for code in c.wrongly_suppressed]
        rows += [(c.chart_id, code, "Rightly suppressed (code was wrong)") for code in c.correctly_suppressed]
    if rows:
        st.dataframe(pd.DataFrame(rows, columns=["Chart", "Code", "Verdict"]), hide_index=True)

with tab_code:
    st.markdown("Which codes the NLP gets right or wrong most often (delivered view), worst first.")
    st.dataframe(
        pd.DataFrame(
            [
                {
                    "Code": x.code,
                    "Correct (TP)": x.tp,
                    "Wrong delivered (FP)": x.fp,
                    "Wrongly suppressed": x.wrongly_suppressed,
                    "Missed": x.missed,
                    "Rightly suppressed": x.correctly_suppressed,
                    "Errors": x.errors,
                }
                for x in result.by_code
            ]
        ),
        hide_index=True,
    )

with tab_data:
    if result.warnings:
        for message in result.warnings:
            st.warning(message)
    else:
        st.success("No data-quality warnings.")
    st.markdown("**Ground truth as read**")
    st.dataframe(pd.DataFrame([(k, ", ".join(v)) for k, v in sorted(truth.items())], columns=["Chart", "Codes"]), hide_index=True)
    st.markdown("**NLP output as read**")
    st.dataframe(
        pd.DataFrame(
            [(k, ", ".join(v["suggested"]), ", ".join(v["suppressed"])) for k, v in sorted(nlp.items())],
            columns=["Chart", "Suggested", "Suppressed"],
        ),
        hide_index=True,
    )

with tab_how:
    st.markdown(
        """
**Each chart is compared as two sets of codes.**

| Name | Meaning | Effect |
|---|---|---|
| **TP**, true positive | NLP *suggested* it and it is in the ground truth | good |
| **FP**, false positive | NLP *suggested* it but it is not in the ground truth | lowers precision |
| **FN**, false negative | in the ground truth but NLP did not *suggest* it (suppressed, or never found) | lowers recall |
| rightly suppressed | NLP suppressed it and it is not in the ground truth | good (a false positive avoided) |

**Formulas**

* Precision = TP / (TP + FP): of the codes shown, how many were right?
* Recall = TP / (TP + FN): of the codes that should be found, how many were?
* F1 = 2TP / (2TP + FP + FN): one number balancing both.
* Accuracy = TP / (TP + FP + FN): share of all codes involved that were agreed on (set accuracy).

**Undefined, not zero.** If nothing was suggested, precision is *n/a*; if the chart has no ground-truth codes, recall is *n/a*.
Per-chart averages skip undefined charts; pooled (micro) numbers add up all counts first.

Full explanation with a worked example: `docs/HOW_IT_WORKS.md`.
"""
    )
