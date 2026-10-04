package com.example.chatbot.eval;

import com.example.chatbot.chat.Answer;
import com.example.chatbot.chat.Answer.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Decides, with plain rules and no second model, whether one answer is right. */
public final class Scorer {

    /**
     * @param correct       the answer itself met every expectation (retrieval is reported separately)
     * @param retrievalHit  an expected source was among the retrieved passages (null when none was expected)
     * @param reasons       why it failed (empty when everything passed)
     */
    public record Score(boolean correct, Boolean retrievalHit, List<String> reasons) {
    }

    private Scorer() {
    }

    public static Score score(EvalQuestion q, Answer a) {
        List<String> answerProblems = new ArrayList<>();
        String text = a.answer() == null ? "" : a.answer();

        if (q.expectRefusal()) {
            if (a.mode() != Mode.NO_ANSWER) {
                answerProblems.add("should have declined to answer, but the mode was " + a.mode());
            }
        } else {
            if (a.mode() != Mode.ANSWER) {
                answerProblems.add("expected an answer, but the mode was " + a.mode());
            }
            for (String regex : q.mustContainOrEmpty()) {
                if (!find(regex, text)) {
                    answerProblems.add("answer does not match required /" + regex + "/");
                }
            }
        }
        for (String regex : q.mustNotContainOrEmpty()) {
            if (find(regex, text)) {
                answerProblems.add("answer contains forbidden /" + regex + "/");
            }
        }

        Boolean hit = null;
        List<String> reasons = new ArrayList<>(answerProblems);
        if (!q.expectRefusal() && !q.expectSourcesOrEmpty().isEmpty()) {
            hit = a.retrieved().stream().anyMatch(s -> q.expectSourcesOrEmpty().contains(s.source()));
            if (!hit) {
                reasons.add("none of the expected sources " + q.expectSourcesOrEmpty() + " was retrieved");
            }
        }
        return new Score(answerProblems.isEmpty(), hit, reasons);
    }

    private static boolean find(String regex, String text) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE).matcher(text).find();
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Bad regular expression in the evaluation questions: " + regex, e);
        }
    }
}
