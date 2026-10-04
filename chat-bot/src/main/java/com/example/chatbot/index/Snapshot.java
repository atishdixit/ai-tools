package com.example.chatbot.index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable, complete picture of the search index at one moment: passages, their vectors, the keyword index and
 * which file versions it was built from. Re-indexing builds a new snapshot and swaps it in, so a question being answered
 * never sees a half-updated index.
 */
public final class Snapshot {

    private final List<Chunk> chunks;
    private final VectorIndex vectors;
    private final Bm25Index keywords;
    private final Map<String, String> manifest;
    private final String embedModel;

    /** @param vectors parallel to {@code chunks}, entries may be null */
    public Snapshot(List<Chunk> chunks, float[][] vectors, Map<String, String> manifest, String embedModel) {
        if (vectors.length != chunks.size()) {
            throw new IllegalArgumentException("vectors and chunks must have the same length");
        }
        this.chunks = List.copyOf(chunks);
        this.vectors = new VectorIndex(vectors);
        this.keywords = new Bm25Index(chunks.stream().map(Chunk::text).toList());
        this.manifest = Map.copyOf(manifest);
        this.embedModel = embedModel;
    }

    public static Snapshot empty(String embedModel) {
        return new Snapshot(List.of(), new float[0][], Map.of(), embedModel);
    }

    public List<Chunk> chunks() {
        return chunks;
    }

    public VectorIndex vectors() {
        return vectors;
    }

    public Bm25Index keywords() {
        return keywords;
    }

    /** file name (relative) to the SHA-256 of the content that was indexed */
    public Map<String, String> manifest() {
        return manifest;
    }

    public String embedModel() {
        return embedModel;
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public boolean hasVectors() {
        return vectors.hasAny();
    }

    public boolean vectorsComplete() {
        return vectors.isComplete();
    }

    public Map<String, Integer> chunksPerSource() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Chunk c : chunks) {
            counts.merge(c.source(), 1, Integer::sum);
        }
        return counts;
    }
}
