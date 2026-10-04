package com.example.chatbot.llm;

import java.util.List;

/** Turns text into vectors. Documents and questions are embedded differently on purpose (see the prefixes in the config). */
public interface Embedder {

    /** One vector per text, in the same order. */
    List<float[]> embedDocuments(List<String> texts) throws OllamaException;

    float[] embedQuery(String question) throws OllamaException;
}
