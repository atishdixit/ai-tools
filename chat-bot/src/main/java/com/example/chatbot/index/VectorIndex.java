package com.example.chatbot.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Meaning-based search: each passage is a vector, and the closest vectors to the question's vector are the most relevant
 * passages. Vectors are length-normalised, so the dot product equals cosine similarity. A passage may have no vector (the
 * embedding model was unavailable when it was indexed); such passages are simply skipped here.
 */
public final class VectorIndex {

    public record Scored(int index, double cosine) {
    }

    private final float[][] vectors;

    /** @param vectors one per passage, {@code null} where missing; they are normalised on the way in */
    public VectorIndex(float[][] vectors) {
        this.vectors = new float[vectors.length][];
        for (int i = 0; i < vectors.length; i++) {
            this.vectors[i] = vectors[i] == null ? null : normalise(vectors[i]);
        }
    }

    public boolean hasAny() {
        for (float[] v : vectors) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isComplete() {
        for (float[] v : vectors) {
            if (v == null) {
                return false;
            }
        }
        return true;
    }

    public float[] vector(int index) {
        return vectors[index];
    }

    /** Closest first. */
    public List<Scored> search(float[] query, int k) {
        float[] q = normalise(query);
        List<Scored> all = new ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            float[] v = vectors[i];
            if (v == null || v.length != q.length) {
                continue;
            }
            all.add(new Scored(i, dot(q, v)));
        }
        all.sort(Comparator.comparingDouble(Scored::cosine).reversed());
        return all.size() > k ? new ArrayList<>(all.subList(0, k)) : all;
    }

    public double cosine(float[] query, int index) {
        float[] v = vectors[index];
        return v == null || v.length != query.length ? Double.NaN : dot(normalise(query), v);
    }

    static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    static float[] normalise(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        double length = Math.sqrt(sum);
        if (length == 0) {
            return v.clone();
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / length);
        }
        return out;
    }
}
