from __future__ import annotations

from pathlib import Path

import pytest

from pra import InputError, normalize_code, parse_ground_truth, parse_nlp, read_text

DATA = Path(__file__).resolve().parent.parent / "data"


def test_csv_and_json_describe_the_same_data():
    assert parse_ground_truth(read_text(DATA / "ground_truth.csv")) == parse_ground_truth(read_text(DATA / "ground_truth.json"))
    assert parse_nlp(read_text(DATA / "nlp_output.csv")) == parse_nlp(read_text(DATA / "nlp_output.json"))


def test_blank_code_row_registers_a_chart_with_no_codes():
    truth = parse_ground_truth("chart_id,code\nA,I10\nB,\n")
    assert truth == {"A": ["I10"], "B": []}


def test_header_names_are_forgiving():
    truth = parse_ground_truth("Chart ID,ICD-10\nA,I10\n")
    assert truth == {"A": ["I10"]}
    nlp = parse_nlp("ChartId,Code,Type\nA,I10,Suggestion\nA,E11.9,Suppression\n")
    assert nlp == {"A": {"suggested": ["I10"], "suppressed": ["E11.9"]}}


def test_json_list_forms():
    assert parse_ground_truth('[{"chart_id": "A", "codes": ["I10"]}]') == {"A": ["I10"]}
    assert parse_nlp('[{"chart_id": "A", "suggested": ["I10"], "suppressed": "E11.9, E78.5"}]') == {
        "A": {"suggested": ["I10"], "suppressed": ["E11.9", "E78.5"]}
    }


@pytest.mark.parametrize(
    ("loader", "text", "fragment"),
    [
        (parse_ground_truth, "", "empty"),
        (parse_ground_truth, "foo,bar\n1,2\n", "missing column"),
        (parse_nlp, "chart_id,code\nA,I10\n", "status"),
        (parse_nlp, "chart_id,code,status\nA,I10,maybe\n", "unknown status"),
        (parse_ground_truth, "{not json", "not valid JSON"),
        (parse_nlp, '{"A": ["I10"]}', "expected an object"),
    ],
)
def test_bad_input_gives_a_message_a_person_can_act_on(loader, text, fragment):
    with pytest.raises(InputError) as error:
        loader(text)
    assert fragment in str(error.value)


@pytest.mark.parametrize(
    ("code", "mode", "expected"),
    [
        (" E11.9 ", "exact", "E11.9"),
        ("e11.9", "exact", "e11.9"),
        ("e11.9", "normalized", "E119"),
        ("E-11 9", "normalized", "E119"),
        ("E11.65", "icd10_category", "E11"),
        ("m54.5", "icd10_category", "M54"),
    ],
)
def test_normalize_code(code, mode, expected):
    assert normalize_code(code, mode) == expected


def test_unknown_mode_is_rejected():
    with pytest.raises(ValueError):
        normalize_code("I10", "fuzzy")
