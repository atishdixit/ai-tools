"""Code matching: when are two medical codes "the same code"?

The ground truth ("Visionary") and the NLP API do not always write a code the same way (``E11.9`` / ``e119`` / ``E11``), and sometimes
the NLP returns a parent code where a coder chose a more specific child. How strict the comparison is changes the numbers, so it is an
explicit, visible choice (``mode``) rather than something buried in the code.
"""

from __future__ import annotations

import re

EXACT = "exact"
NORMALIZED = "normalized"
ICD10_CATEGORY = "icd10_category"

MODES = (EXACT, NORMALIZED, ICD10_CATEGORY)
DEFAULT_MODE = NORMALIZED

MODE_LABELS = {
    EXACT: "Exact: the text must match exactly (case and punctuation count)",
    NORMALIZED: "Normalized (default): ignore case, dots, dashes and spaces (E11.9 = e119)",
    ICD10_CATEGORY: "ICD-10 category: compare only the first 3 characters (E11.9 = E11.65)",
}

_SEPARATORS = re.compile(r"[\s.\-_]")


def normalize_code(code: str, mode: str = DEFAULT_MODE) -> str:
    """Return the comparison key of ``code`` for the given matching ``mode``.

    * ``exact``: only leading/trailing whitespace is removed.
    * ``normalized``: upper-case, with spaces, dots, dashes and underscores removed, so ``e11.9`` and ``E119`` are equal.
    * ``icd10_category``: the normalized code cut to its first three characters (the ICD-10 category). Meant for ICD-10 only:
      applied to 5-digit procedure codes it would merge unrelated codes.
    """
    if mode not in MODES:
        raise ValueError(f"unknown matching mode {mode!r}; expected one of {MODES}")
    text = str(code).strip()
    if mode == EXACT:
        return text
    key = _SEPARATORS.sub("", text).upper()
    if mode == NORMALIZED:
        return key
    return key[:3]
