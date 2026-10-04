package com.example.chatbot.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keyword search with the BM25 formula. Good at exact names, numbers and codes ("ISO 27001", "99.9"), which meaning-based
 * (embedding) search can blur. Immutable after construction, so safe to share between threads.
 */
public final class Bm25Index {

    /** @param coverage the share of distinct query terms that appear in this passage (0..1) */
    public record Scored(int index, double score, double coverage) {
    }

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<Map<String, Integer>> termFrequencies = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final double averageLength;

    public Bm25Index(List<String> documents) {
        lengths = new int[documents.size()];
        long total = 0;
        for (int i = 0; i < documents.size(); i++) {
            List<String> terms = Tokenizer.terms(documents.get(i));
            Map<String, Integer> tf = new HashMap<>();
            for (String t : terms) {
                tf.merge(t, 1, Integer::sum);
            }
            termFrequencies.add(tf);
            lengths[i] = terms.size();
            total += terms.size();
            for (String t : tf.keySet()) {
                documentFrequency.merge(t, 1, Integer::sum);
            }
        }
        averageLength = documents.isEmpty() ? 0 : (double) total / documents.size();
    }

    public int size() {
        return lengths.length;
    }

    /** Best matches first; passages that share no term with the query are not returned. */
    public List<Scored> search(String query, int k) {
        Set<String> queryTerms = new HashSet<>(Tokenizer.terms(query));
        if (queryTerms.isEmpty() || lengths.length == 0) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < lengths.length; i++) {
            Map<String, Integer> tf = termFrequencies.get(i);
            double score = 0;
            int matched = 0;
            for (String term : queryTerms) {
                Integer f = tf.get(term);
                if (f == null) {
                    continue;
                }
                matched++;
                int df = documentFrequency.get(term);
                double idf = Math.log(1 + (lengths.length - df + 0.5) / (df + 0.5));
                double norm = f + K1 * (1 - B + B * lengths[i] / Math.max(1.0, averageLength));
                score += idf * f * (K1 + 1) / norm;
            }
            if (matched > 0) {
                scored.add(new Scored(i, score, (double) matched / queryTerms.size()));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.size() > k ? new ArrayList<>(scored.subList(0, k)) : scored;
    }
}
