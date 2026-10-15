"""Smoke tests of the Streamlit UI, run headless with Streamlit's own test harness (no browser, no server)."""

from __future__ import annotations

from pathlib import Path

from streamlit.testing.v1 import AppTest

APP = str(Path(__file__).resolve().parent.parent / "app.py")


def _run() -> AppTest:
    app = AppTest.from_file(APP, default_timeout=120)  # generous: the very first import of Streamlit on a cold machine can be slow
    app.run()
    return app


def _metric_values(app: AppTest) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    for metric in app.metric:
        found.setdefault(metric.label, []).append(metric.value)
    return found


def test_sample_data_renders_the_hand_checked_numbers():
    app = _run()
    assert not app.exception
    values = _metric_values(app)
    # delivered view first, raw view second (pooled / micro averaging is the default)
    assert values["Precision"][:2] == ["61.5%", "61.1%"]
    assert values["Recall"][:2] == ["53.3%", "73.3%"]
    assert values["F1"][:2] == ["57.1%", "66.7%"]
    assert values["Accuracy"][:2] == ["40.0%", "50.0%"]


def test_changing_the_matching_mode_changes_the_numbers():
    app = _run()
    app.sidebar.selectbox[0].set_value("exact").run()
    assert not app.exception
    # exact matching: "z79.01" no longer equals "Z79.01"  ->  TP 7, FP 6 -> precision 7/13
    assert _metric_values(app)["Precision"][0] == "53.8%"


def test_macro_averaging_is_selectable():
    app = _run()
    app.sidebar.radio[1].set_value("macro").run()
    assert not app.exception
    assert _metric_values(app)["Precision"][0] == "48.6%"


def test_the_upload_mode_asks_for_files_instead_of_failing():
    app = _run()
    app.sidebar.radio[0].set_value("Upload my own files").run()
    assert not app.exception
    assert any("Upload both files" in info.value for info in app.info)
