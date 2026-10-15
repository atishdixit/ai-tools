"""Command line:  python -m pra data/ground_truth.csv data/nlp_output.csv [--mode normalized]"""

from __future__ import annotations

import argparse
import sys

from . import DEFAULT_MODE, MODES, InputError, evaluate, parse_ground_truth, parse_nlp, read_text
from .report import text_report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m pra", description="Precision / recall of NLP medical codes against a ground truth.")
    parser.add_argument("ground_truth", help="CSV or JSON file: the master of truth (Visionary codes per chart)")
    parser.add_argument("nlp_output", help="CSV or JSON file: NLP suggested and suppressed codes per chart")
    parser.add_argument("--mode", choices=MODES, default=DEFAULT_MODE, help="how strictly codes are matched (default: %(default)s)")
    args = parser.parse_args(argv)
    try:
        truth = parse_ground_truth(read_text(args.ground_truth), source=args.ground_truth)
        nlp = parse_nlp(read_text(args.nlp_output), source=args.nlp_output)
    except (InputError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    print(text_report(evaluate(truth, nlp, args.mode)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
