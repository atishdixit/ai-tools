package com.example.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny stand-in for Ollama's HTTP API, so the real client, the whole pipeline and the web layer can be tested without a
 * model. Embeddings are the bag-of-words vectors from {@link TestSupport}. The "language model" answers from the passages:
 * it replies with the first passage line that shares the most words with the question, cites [1], or declines when the
 * question contains the word "unanswerable".
 */
public final class FakeOllamaServer implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    public final List<JsonNode> chatRequests = new CopyOnWriteArrayList<>();
    public final List<JsonNode> embedRequests = new CopyOnWriteArrayList<>();
    public final AtomicInteger chatCalls = new AtomicInteger();
    public volatile List<String> models = List.of("chat-model:latest", "embed-model:latest");
    public volatile int chatStatus = 200;
    public volatile String chatError;
    public volatile long chatDelayMillis;
    public volatile boolean errorInStream;

    public FakeOllamaServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", this::tags);
        server.createContext("/api/embed", this::embed);
        server.createContext("/api/chat", this::chat);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void tags(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{\"models\":[");
        for (int i = 0; i < models.size(); i++) {
            sb.append(i == 0 ? "" : ",").append("{\"name\":\"").append(models.get(i)).append("\"}");
        }
        send(ex, 200, sb.append("]}").toString());
    }

    private void embed(HttpExchange ex) throws IOException {
        JsonNode body = mapper.readTree(ex.getRequestBody());
        embedRequests.add(body);
        StringBuilder sb = new StringBuilder("{\"embeddings\":[");
        boolean first = true;
        for (JsonNode input : body.path("input")) {
            // like the real model, treat the document/query label as a label, not as part of the text
            float[] v = TestSupport.embed(input.asText().replaceFirst("^search_(document|query): ", ""));
            sb.append(first ? "" : ",").append('[');
            for (int i = 0; i < v.length; i++) {
                sb.append(i == 0 ? "" : ",").append(v[i]);
            }
            sb.append(']');
            first = false;
        }
        send(ex, 200, sb.append("]}").toString());
    }

    private void chat(HttpExchange ex) throws IOException {
        JsonNode body = mapper.readTree(ex.getRequestBody());
        chatRequests.add(body);
        chatCalls.incrementAndGet();
        if (chatDelayMillis > 0) {
            try {
                Thread.sleep(chatDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (chatStatus != 200) {
            send(ex, chatStatus, chatError == null ? "{\"error\":\"failure\"}" : chatError);
            return;
        }
        String answer = answerFor(body);
        List<String> pieces = new ArrayList<>();
        for (String word : answer.split("(?<= )")) {
            pieces.add(word);
        }
        ex.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            for (String piece : pieces) {
                line(out, "{\"message\":{\"role\":\"assistant\",\"content\":" + mapper.writeValueAsString(piece) + "},\"done\":false}");
            }
            if (errorInStream) {
                line(out, "{\"error\":\"the model crashed\"}");
                return;
            }
            line(out, "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}");
        }
    }

    /** The fake model: decline for "unanswerable", otherwise quote the best-matching line of the first passage. */
    private static String answerFor(JsonNode body) {
        JsonNode messages = body.path("messages");
        String last = messages.get(messages.size() - 1).path("content").asText();
        int q = last.indexOf("QUESTION:");
        String question = q < 0 ? "" : last.substring(q + 9).strip();
        if (question.toLowerCase().contains("unanswerable")) {
            return "I don't have that information in the company documents.";
        }
        String context = q < 0 ? last : last.substring(0, q);
        var wanted = com.example.chatbot.index.Tokenizer.terms(question);
        String best = "";
        int bestScore = -1;
        for (String line : context.split("\n")) {
            if (line.startsWith("[") || line.startsWith("<") || line.startsWith("CONTEXT")) {
                continue;
            }
            int score = 0;
            for (String t : com.example.chatbot.index.Tokenizer.terms(line)) {
                if (wanted.contains(t)) {
                    score++;
                }
            }
            if (score > bestScore || (score == bestScore && line.strip().length() > best.length())) { // ties go to the longer line, not the heading
                bestScore = score;
                best = line.strip();
            }
        }
        return best + " [1]";
    }

    private static void line(OutputStream out, String json) throws IOException {
        out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
