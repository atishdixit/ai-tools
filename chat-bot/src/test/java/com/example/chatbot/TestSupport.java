package com.example.chatbot;

import com.example.chatbot.config.ChatBotProperties;
import com.example.chatbot.index.Tokenizer;
import com.example.chatbot.llm.Embedder;
import com.example.chatbot.llm.OllamaException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared helpers for the tests: settings, and a deterministic stand-in for the embedding model. */
public final class TestSupport {

    public static final int DIMS = 256;

    private TestSupport() {
    }

    /** Settings equal to application.yml except for the folders and a lower gate (the fake embeddings are cruder). */
    public static ChatBotProperties props(Path dataDir, Path indexDir) {
        return new ChatBotProperties(
                "Test Co", dataDir, indexDir, false,
                new ChatBotProperties.Ollama("http://localhost:1", "chat-model", "embed-model", 0, 42, 4096, 350, 16, 2, 5, 5,
                        "search_document: ", "search_query: "),
                new ChatBotProperties.Retrieval(4, 20, 0.15, 0.6, 900, 120, 0, true),
                new ChatBotProperties.Chat(1000, 4, 7, 60, 100, 2, 1),
                new ChatBotProperties.Eval(false, Path.of("eval/questions.json"), Path.of("eval/report.md"), Path.of("eval/adversarial-data"), 0.8));
    }

    /** A bag-of-words embedding: words are hashed into a fixed number of slots, so texts sharing words have similar vectors. */
    public static float[] embed(String text) {
        float[] v = new float[DIMS];
        for (String term : Tokenizer.terms(text)) {
            v[Math.floorMod(term.hashCode(), DIMS)] += 1f;
        }
        return v;
    }

    /** Deterministic fake that counts calls and can be switched off to simulate Ollama being down. */
    public static final class FakeEmbedder implements Embedder {
        public final AtomicInteger documentTexts = new AtomicInteger();
        public final AtomicInteger queries = new AtomicInteger();
        public volatile boolean down;

        @Override
        public List<float[]> embedDocuments(List<String> texts) throws OllamaException {
            if (down) {
                throw new OllamaException("Ollama is not running");
            }
            documentTexts.addAndGet(texts.size());
            List<float[]> out = new ArrayList<>();
            texts.forEach(t -> out.add(embed(t)));
            return out;
        }

        @Override
        public float[] embedQuery(String question) throws OllamaException {
            if (down) {
                throw new OllamaException("Ollama is not running");
            }
            queries.incrementAndGet();
            return embed(question);
        }
    }
}
