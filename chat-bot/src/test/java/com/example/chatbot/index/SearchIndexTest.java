package com.example.chatbot.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.chatbot.TestSupport;
import com.example.chatbot.index.HybridRetriever.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tokenizer, BM25, vector search and the hybrid merge. */
class SearchIndexTest {

    private static final List<String> DOCS = List.of(
            "Pricing > RouteWise\nThe Growth plan costs Rs 14,999 per month and includes 25 vehicles.",
            "Support\nEmail support is monitored around the clock. Phone support runs on weekdays.",
            "Security\nCustomer data is encrypted with AES-256 and the company is ISO 27001 certified.",
            "HR\nEmployees receive 22 days of annual leave each year.");

    private static Snapshot snapshot(List<String> docs) {
        List<Chunk> chunks = new ArrayList<>();
        float[][] vectors = new float[docs.size()][];
        for (int i = 0; i < docs.size(); i++) {
            chunks.add(new Chunk("f#" + i, "f" + i + ".md", "t", docs.get(i)));
            vectors[i] = TestSupport.embed(docs.get(i));
        }
        return new Snapshot(chunks, vectors, Map.of(), "m");
    }

    // ------------------------------------------------------------------ tokenizer

    @Test
    void tokenizerLowercasesDropsFillerWordsAndFoldsPlurals() {
        assertThat(Tokenizer.terms("What are the Pricing Plans?")).containsExactly("pricing", "plan");
        assertThat(Tokenizer.terms("policies and policy")).containsExactly("policy", "policy");
        assertThat(Tokenizer.terms("status of access")).containsExactly("status", "access");
    }

    @Test
    void tokenizerKeepsNumbersAndCodes() {
        assertThat(Tokenizer.terms("ISO 27001, 99.9% and ap-south-1")).containsExactly("iso", "27001", "99", "9", "ap", "south", "1");
    }

    @Test
    void tokenizerCopesWithNullAndEmpty() {
        assertThat(Tokenizer.terms(null)).isEmpty();
        assertThat(Tokenizer.terms("")).isEmpty();
        assertThat(Tokenizer.terms("the of and")).isEmpty();
    }

    // ------------------------------------------------------------------ BM25

    @Test
    void keywordSearchFindsTheExactTermAndRanksItFirst() {
        Bm25Index index = new Bm25Index(DOCS);
        List<Bm25Index.Scored> hits = index.search("What is ISO 27001?", 3);
        assertThat(hits.get(0).index()).isEqualTo(2);
    }

    @Test
    void pluralAndSingularMatch() {
        assertThat(new Bm25Index(DOCS).search("vehicle", 3).get(0).index()).isEqualTo(0);
    }

    @Test
    void passagesWithoutAnyQueryTermAreNotReturned() {
        assertThat(new Bm25Index(DOCS).search("helicopter", 5)).isEmpty();
        assertThat(new Bm25Index(DOCS).search("the of and", 5)).isEmpty();
    }

    @Test
    void coverageIsTheShareOfQueryTermsFound() {
        Bm25Index.Scored top = new Bm25Index(DOCS).search("annual leave helicopter", 1).get(0);
        assertThat(top.index()).isEqualTo(3);
        assertThat(top.coverage()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void anEmptyIndexIsSafe() {
        assertThat(new Bm25Index(List.of()).search("anything", 3)).isEmpty();
    }

    // ------------------------------------------------------------------ vectors

    @Test
    void theClosestVectorComesFirst() {
        VectorIndex index = new VectorIndex(new float[][] {{1, 0}, {0.8f, 0.6f}, {0, 1}});
        List<VectorIndex.Scored> hits = index.search(new float[] {1, 0}, 3);
        assertThat(hits).extracting(VectorIndex.Scored::index).containsExactly(0, 1, 2);
        assertThat(hits.get(0).cosine()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(hits.get(2).cosine()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void lengthDoesNotMatterOnlyDirection() {
        VectorIndex index = new VectorIndex(new float[][] {{10, 0}, {0, 0.001f}});
        assertThat(index.search(new float[] {3, 0}, 1).get(0).index()).isEqualTo(0);
    }

    @Test
    void missingVectorsAreSkippedAndReported() {
        VectorIndex index = new VectorIndex(new float[][] {null, {1, 0}});
        assertThat(index.hasAny()).isTrue();
        assertThat(index.isComplete()).isFalse();
        assertThat(index.search(new float[] {1, 0}, 5)).extracting(VectorIndex.Scored::index).containsExactly(1);
        assertThat(new VectorIndex(new float[][] {null}).hasAny()).isFalse();
    }

    @Test
    void vectorsOfAnotherSizeAreIgnoredNotCrashed() {
        VectorIndex index = new VectorIndex(new float[][] {{1, 0, 0}, {1, 0}});
        assertThat(index.search(new float[] {1, 0}, 5)).extracting(VectorIndex.Scored::index).containsExactly(1);
    }

    @Test
    void aZeroVectorDoesNotProduceNaN() {
        VectorIndex index = new VectorIndex(new float[][] {{0, 0}, {1, 0}});
        assertThat(index.search(new float[] {1, 0}, 2)).allSatisfy(s -> assertThat(Double.isNaN(s.cosine())).isFalse());
    }

    // ------------------------------------------------------------------ hybrid

    @Test
    void hybridSearchFindsThePassageThatBothSearchesLike() {
        Snapshot s = snapshot(DOCS);
        HybridRetriever.Result r = new HybridRetriever().retrieve(s, "How much is the Growth plan per month?", TestSupport.embed("How much is the Growth plan per month?"), 2, 10);
        assertThat(r.hits().get(0).chunk().id()).isEqualTo("f#0");
        assertThat(r.vectorsUsed()).isTrue();
        assertThat(r.bestCosine()).isGreaterThan(0.3);
    }

    @Test
    void aPassageFoundByBothSearchesOutranksOneFoundByOnlyOne() {
        // "alpha" is in doc 0 (keyword and vector match); doc 1 shares nothing
        Snapshot s = snapshot(List.of("alpha beta gamma", "delta epsilon zeta"));
        HybridRetriever.Result r = new HybridRetriever().retrieve(s, "alpha", TestSupport.embed("alpha"), 2, 10);
        assertThat(r.hits().get(0).chunk().id()).isEqualTo("f#0");
        assertThat(r.hits().get(0).score()).isGreaterThan(1.0 / 61.0); // more than one list contributed
    }

    @Test
    void withoutAQueryVectorKeywordSearchStillWorks() {
        Snapshot s = snapshot(DOCS);
        HybridRetriever.Result r = new HybridRetriever().retrieve(s, "annual leave", null, 2, 10);
        assertThat(r.vectorsUsed()).isFalse();
        assertThat(Double.isNaN(r.bestCosine())).isTrue();
        assertThat(r.hits().get(0).chunk().id()).isEqualTo("f#3");
        assertThat(r.bestCoverage()).isEqualTo(1.0);
        assertThat(Double.isNaN(r.hits().get(0).cosine())).isTrue();
    }

    @Test
    void anEmptyIndexReturnsNothing() {
        HybridRetriever.Result r = new HybridRetriever().retrieve(Snapshot.empty("m"), "x", new float[] {1}, 3, 10);
        assertThat(r.hits()).isEmpty();
        assertThat(r.vectorsUsed()).isFalse();
    }

    @Test
    void topKLimitsTheResult() {
        Snapshot s = snapshot(DOCS);
        assertThat(new HybridRetriever().retrieve(s, "data customer annual plan email", TestSupport.embed("data customer annual plan email"), 2, 10).hits())
                .hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void everyHitCarriesACosineWhenVectorsExist() {
        Snapshot s = snapshot(DOCS);
        HybridRetriever.Result r = new HybridRetriever().retrieve(s, "leave", TestSupport.embed("leave"), 4, 1);
        for (Hit h : r.hits()) {
            assertThat(Double.isNaN(h.cosine())).isFalse();
        }
    }

    @Test
    void snapshotsRejectMismatchedVectorCounts() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Snapshot(List.of(new Chunk("a", "a", "t", "x")), new float[0][], Map.of(), "m"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
