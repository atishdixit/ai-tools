package com.example.chatbot.chat;

import java.util.List;

/**
 * The result of one question.
 *
 * @param answer    text shown to the user
 * @param mode      how it was produced, see {@link Mode}
 * @param sources   the passages the answer rests on: the ones the model cited, or all it was given when it cited none
 * @param retrieved every passage retrieved for the question (for debugging and evaluation)
 * @param notes     things the user may want to know, e.g. that the model was unavailable
 * @param topSimilarity best cosine similarity found (NaN, shown as null, when embeddings were not available)
 */
public record Answer(
        String sessionId,
        String answer,
        Mode mode,
        List<SourceRef> sources,
        List<SourceRef> retrieved,
        List<String> notes,
        Double topSimilarity,
        long retrieveMillis,
        long generateMillis) {

    public enum Mode {
        /** The model answered from the retrieved passages. */
        ANSWER,
        /** The documents do not cover the question (decided before or by the model); nothing was invented. */
        NO_ANSWER,
        /** The model was unavailable, so the most relevant passages are shown as they are. */
        EXTRACTIVE,
        /** No documents have been indexed yet. */
        NO_DOCUMENTS
    }

    public record SourceRef(int n, String source, String title, double score, String excerpt) {
    }
}
