package com.example.chatbot.eval;

import java.util.List;

/**
 * One scored test question, read from {@code eval/questions.json}.
 *
 * @param split          "dev" (used while tuning) or "holdout" (never used for tuning, so its score is the honest one)
 * @param history        earlier questions asked first in the same conversation, for follow-up tests (not scored)
 * @param corpus         null for the company documents, "adversarial" for the poisoned-document test set
 * @param expectSources  at least one of these files should be among the retrieved passages
 * @param mustContain    regular expressions that must ALL match the answer (case-insensitive)
 * @param mustNotContain regular expressions that must match NONE of the answer
 * @param expectRefusal  the bot must decline to answer
 */
public record EvalQuestion(
        String id,
        String split,
        String category,
        String question,
        List<String> history,
        String corpus,
        List<String> expectSources,
        List<String> mustContain,
        List<String> mustNotContain,
        boolean expectRefusal) {

    public List<String> historyOrEmpty() {
        return history == null ? List.of() : history;
    }

    public List<String> expectSourcesOrEmpty() {
        return expectSources == null ? List.of() : expectSources;
    }

    public List<String> mustContainOrEmpty() {
        return mustContain == null ? List.of() : mustContain;
    }

    public List<String> mustNotContainOrEmpty() {
        return mustNotContain == null ? List.of() : mustNotContain;
    }
}
