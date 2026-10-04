package com.example.chatbot.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the passages most relevant to a question by running two searches and merging them:
 * meaning-based (vectors) and keyword-based (BM25). They are merged with reciprocal rank fusion: a passage scores
 * {@code 1 / (60 + rank)} in each list it appears in, and the sum decides the order. Using ranks instead of raw scores
 * avoids having to compare numbers on different scales.
 */
public final class HybridRetriever {

    /** @param cosine similarity to the question, NaN when no vector is available */
    public record Hit(Chunk chunk, double score, double cosine, double keywordScore) {
    }

    /**
     * @param hits           the best passages, best first
     * @param bestCosine     the highest cosine similarity found anywhere in the index (NaN without vectors)
     * @param bestCoverage   the highest share of question terms found in a single passage
     * @param vectorsUsed    whether the meaning-based search took part
     */
    public record Result(List<Hit> hits, double bestCosine, double bestCoverage, boolean vectorsUsed) {
    }

    private static final int RRF_K = 60;

    /** @param queryVector the question's embedding, or null to use keyword search only */
    public Result retrieve(Snapshot snapshot, String query, float[] queryVector, int topK, int candidates) {
        if (snapshot.isEmpty()) {
            return new Result(List.of(), Double.NaN, 0, false);
        }
        List<VectorIndex.Scored> byMeaning = queryVector == null ? List.of() : snapshot.vectors().search(queryVector, candidates);
        List<Bm25Index.Scored> byKeyword = snapshot.keywords().search(query, candidates);

        Map<Integer, Double> fused = new HashMap<>();
        Map<Integer, Double> cosines = new HashMap<>();
        Map<Integer, Double> keywordScores = new HashMap<>();
        for (int rank = 0; rank < byMeaning.size(); rank++) {
            VectorIndex.Scored s = byMeaning.get(rank);
            fused.merge(s.index(), 1.0 / (RRF_K + rank + 1), Double::sum);
            cosines.put(s.index(), s.cosine());
        }
        double bestCoverage = 0;
        for (int rank = 0; rank < byKeyword.size(); rank++) {
            Bm25Index.Scored s = byKeyword.get(rank);
            fused.merge(s.index(), 1.0 / (RRF_K + rank + 1), Double::sum);
            keywordScores.put(s.index(), s.score());
            bestCoverage = Math.max(bestCoverage, s.coverage());
        }

        List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(fused.entrySet());
        ranked.sort(Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())));

        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : ranked.subList(0, Math.min(topK, ranked.size()))) {
            int i = e.getKey();
            double cosine = cosines.containsKey(i)
                    ? cosines.get(i)
                    : (queryVector == null ? Double.NaN : snapshot.vectors().cosine(queryVector, i));
            hits.add(new Hit(snapshot.chunks().get(i), e.getValue(), cosine, keywordScores.getOrDefault(i, 0.0)));
        }
        double bestCosine = byMeaning.isEmpty() ? Double.NaN : byMeaning.get(0).cosine();
        return new Result(hits, bestCosine, bestCoverage, queryVector != null && !byMeaning.isEmpty());
    }
}
