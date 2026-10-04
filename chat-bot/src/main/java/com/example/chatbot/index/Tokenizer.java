package com.example.chatbot.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Splits text into search terms: lower case, letters and digits only, no filler words, plurals folded. */
public final class Tokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "am", "of", "to", "in", "on", "at", "for", "and", "or",
            "do", "does", "did", "what", "which", "who", "whom", "how", "when", "where", "why", "can", "could", "would", "should",
            "i", "you", "we", "it", "this", "that", "these", "those", "with", "by", "as", "from", "your", "our", "my", "me", "us",
            "about", "tell", "please", "there", "any", "has", "have", "had", "if", "so", "than", "then", "its", "their", "they",
            "s", "get", "got");

    private Tokenizer() {
    }

    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.isEmpty() || STOP_WORDS.contains(raw)) {
                continue;
            }
            out.add(fold(raw));
        }
        return out;
    }

    /** Folds a simple plural ("plans" to "plan", "policies" to "policy") so singular and plural match. */
    static String fold(String term) {
        if (term.length() > 4 && term.endsWith("ies")) {
            return term.substring(0, term.length() - 3) + "y";
        }
        if (term.length() > 3 && term.endsWith("s") && !term.endsWith("ss") && !term.endsWith("us")) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }
}
