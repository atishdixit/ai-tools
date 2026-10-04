package com.example.chatbot.llm;

import com.example.chatbot.config.ChatBotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Talks to a local Ollama server over its HTTP API: {@code /api/embed} for vectors, {@code /api/chat} for answers
 * (streamed, one JSON object per line) and {@code /api/tags} to see which models are installed.
 */
@Component
public class OllamaClient implements Embedder, ChatModel {

    private final ChatBotProperties.Ollama config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public OllamaClient(ChatBotProperties props) {
        this.config = props.ollama();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .build();
    }

    // ------------------------------------------------------------------ status

    /** The names of the installed models, or an exception if the server cannot be reached. */
    public List<String> installedModels() throws OllamaException {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/tags")).timeout(Duration.ofSeconds(3)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new OllamaException("Ollama answered HTTP " + response.statusCode());
            }
            List<String> names = new ArrayList<>();
            for (JsonNode m : mapper.readTree(response.body()).path("models")) {
                names.add(m.path("name").asText());
            }
            return names;
        } catch (IOException e) {
            throw unreachable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Interrupted while contacting Ollama", e);
        }
    }

    public boolean isModelInstalled(List<String> installed, String model) {
        String wanted = model.contains(":") ? model : model + ":latest";
        return installed.stream().anyMatch(n -> n.equals(wanted) || n.equals(model));
    }

    // ------------------------------------------------------------------ embeddings

    @Override
    public List<float[]> embedDocuments(List<String> texts) throws OllamaException {
        List<float[]> all = new ArrayList<>(texts.size());
        int batch = Math.max(1, config.embedBatchSize());
        for (int from = 0; from < texts.size(); from += batch) {
            List<String> part = new ArrayList<>();
            for (String t : texts.subList(from, Math.min(texts.size(), from + batch))) {
                part.add(config.documentPrefix() + t);
            }
            all.addAll(embed(part));
        }
        return all;
    }

    @Override
    public float[] embedQuery(String question) throws OllamaException {
        return embed(List.of(config.queryPrefix() + question)).get(0);
    }

    private List<float[]> embed(List<String> inputs) throws OllamaException {
        ObjectNode body = mapper.createObjectNode().put("model", config.embedModel()).put("truncate", true);
        ArrayNode array = body.putArray("input");
        inputs.forEach(array::add);
        JsonNode response = postJson("/api/embed", body, config.embedTimeoutSeconds());
        JsonNode embeddings = response.path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != inputs.size()) {
            throw new OllamaException("Ollama returned " + embeddings.size() + " embeddings for " + inputs.size() + " texts");
        }
        List<float[]> vectors = new ArrayList<>(inputs.size());
        for (JsonNode e : embeddings) {
            float[] v = new float[e.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) e.get(i).asDouble();
            }
            vectors.add(v);
        }
        return vectors;
    }

    // ------------------------------------------------------------------ chat

    @Override
    public String chat(List<ChatMessage> messages, Consumer<String> onToken) throws OllamaException {
        ObjectNode body = mapper.createObjectNode().put("model", config.chatModel()).put("stream", true).put("keep_alive", "30m");
        ArrayNode array = body.putArray("messages");
        for (ChatMessage m : messages) {
            array.addObject().put("role", m.role()).put("content", m.content());
        }
        body.putObject("options")
                .put("temperature", config.temperature())
                .put("seed", config.seed())
                .put("num_ctx", config.numCtx())
                .put("num_predict", config.numPredict());

        HttpRequest request = jsonRequest("/api/chat", body, config.chatTimeoutSeconds());
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                if (response.statusCode() != 200) {
                    throw failure(response.statusCode(), new String(stream.readAllBytes(), StandardCharsets.UTF_8), config.chatModel());
                }
                StringBuilder full = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = mapper.readTree(line);
                    if (node.hasNonNull("error")) {
                        throw new OllamaException("Ollama error: " + node.get("error").asText());
                    }
                    String piece = node.path("message").path("content").asText("");
                    if (!piece.isEmpty()) {
                        full.append(piece);
                        onToken.accept(piece);
                    }
                    if (node.path("done").asBoolean(false)) {
                        break;
                    }
                }
                return full.toString();
            }
        } catch (HttpTimeoutException e) {
            throw new OllamaException("The model took longer than " + config.chatTimeoutSeconds() + " seconds to answer", e);
        } catch (IOException e) {
            throw unreachable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Interrupted while waiting for the model", e);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private JsonNode postJson(String path, ObjectNode body, int timeoutSeconds) throws OllamaException {
        try {
            HttpResponse<String> response = http.send(jsonRequest(path, body, timeoutSeconds),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw failure(response.statusCode(), response.body(), body.path("model").asText());
            }
            return mapper.readTree(response.body());
        } catch (HttpTimeoutException e) {
            throw new OllamaException("Ollama did not answer within " + timeoutSeconds + " seconds", e);
        } catch (IOException e) {
            throw unreachable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Interrupted while contacting Ollama", e);
        }
    }

    private HttpRequest jsonRequest(String path, ObjectNode body, int timeoutSeconds) throws OllamaException {
        try {
            return HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new OllamaException("Could not build the request", e);
        }
    }

    private URI uri(String path) {
        String base = config.baseUrl().endsWith("/") ? config.baseUrl().substring(0, config.baseUrl().length() - 1) : config.baseUrl();
        return URI.create(base + path);
    }

    private OllamaException failure(int status, String body, String model) {
        String detail = body;
        try {
            JsonNode node = mapper.readTree(body);
            if (node.hasNonNull("error")) {
                detail = node.get("error").asText();
            }
        } catch (IOException ignored) {
            // not JSON: use the raw text
        }
        if (status == 404 && detail.toLowerCase().contains("not found")) {
            return new OllamaException("The model '" + model + "' is not installed. Run:  ollama pull " + model);
        }
        return new OllamaException("Ollama answered HTTP " + status + ": " + detail);
    }

    private OllamaException unreachable(IOException e) {
        if (e instanceof ConnectException || e.getCause() instanceof ConnectException) {
            return new OllamaException("Ollama is not running at " + config.baseUrl() + ". Start it (the Ollama app, or `ollama serve`).", e);
        }
        return new OllamaException("Could not reach Ollama at " + config.baseUrl() + ": " + e.getMessage(), e);
    }
}
