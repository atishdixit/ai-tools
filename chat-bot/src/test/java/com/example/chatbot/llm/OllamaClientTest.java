package com.example.chatbot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.chatbot.FakeOllamaServer;
import com.example.chatbot.TestSupport;
import com.example.chatbot.config.ChatBotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real client against a fake Ollama server. */
class OllamaClientTest {

    private FakeOllamaServer server;

    @BeforeEach
    void start() throws Exception {
        server = new FakeOllamaServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private OllamaClient client(String url, int embedBatch, int chatTimeoutSeconds) {
        ChatBotProperties base = TestSupport.props(Path.of("d"), Path.of("i"));
        ChatBotProperties.Ollama o = base.ollama();
        ChatBotProperties props = new ChatBotProperties(base.companyName(), base.dataDir(), base.indexDir(), false,
                new ChatBotProperties.Ollama(url, o.chatModel(), o.embedModel(), 0, 42, 4096, 350, embedBatch, 2, 5, chatTimeoutSeconds,
                        o.documentPrefix(), o.queryPrefix()),
                base.retrieval(), base.chat(), base.eval());
        return new OllamaClient(props);
    }

    private OllamaClient client() {
        return client(server.url(), 16, 5);
    }

    // ------------------------------------------------------------------ models

    @Test
    void listsInstalledModelsAndMatchesNamesWithOrWithoutTag() throws Exception {
        OllamaClient c = client();
        List<String> installed = c.installedModels();
        assertThat(installed).containsExactly("chat-model:latest", "embed-model:latest");
        assertThat(c.isModelInstalled(installed, "chat-model")).isTrue();
        assertThat(c.isModelInstalled(installed, "chat-model:latest")).isTrue();
        assertThat(c.isModelInstalled(installed, "chat-model:7b")).isFalse();
        assertThat(c.isModelInstalled(installed, "missing")).isFalse();
    }

    // ------------------------------------------------------------------ embeddings

    @Test
    void documentsAreEmbeddedInBatchesWithTheDocumentPrefix() throws Exception {
        OllamaClient c = client(server.url(), 2, 5);
        List<float[]> vectors = c.embedDocuments(List.of("one", "two", "three", "four", "five"));

        assertThat(vectors).hasSize(5);
        assertThat(server.embedRequests).hasSize(3);
        assertThat(server.embedRequests.get(0).path("input")).hasSize(2);
        assertThat(server.embedRequests.get(2).path("input")).hasSize(1);
        for (JsonNode request : server.embedRequests) {
            assertThat(request.path("model").asText()).isEqualTo("embed-model");
            for (JsonNode input : request.path("input")) {
                assertThat(input.asText()).startsWith("search_document: ");
            }
        }
        assertThat(vectors.get(0)).hasSize(TestSupport.DIMS);
    }

    @Test
    void aQuestionIsEmbeddedWithTheQueryPrefix() throws Exception {
        client().embedQuery("what is the price?");
        assertThat(server.embedRequests.get(0).path("input").get(0).asText()).isEqualTo("search_query: what is the price?");
    }

    @Test
    void noTextsMeansNoRequests() throws Exception {
        assertThat(client().embedDocuments(List.of())).isEmpty();
        assertThat(server.embedRequests).isEmpty();
    }

    // ------------------------------------------------------------------ chat

    @Test
    void anAnswerIsStreamedPieceByPieceAndReturnedInFull() throws Exception {
        List<String> pieces = new ArrayList<>();
        String full = client().chat(List.of(ChatMessage.system("rules"), ChatMessage.user("CONTEXT:\n[1] (a.md)\nGrowth costs Rs 14,999.\n\nQUESTION: How much is Growth?")),
                pieces::add);

        assertThat(full).isEqualTo("Growth costs Rs 14,999. [1]");
        assertThat(pieces.size()).isGreaterThan(2);
        assertThat(String.join("", pieces)).isEqualTo(full);
    }

    @Test
    void theRequestCarriesTheModelTheMessagesAndTheSettings() throws Exception {
        client().chat(List.of(ChatMessage.system("rules"), ChatMessage.user("hello\n\nQUESTION: hi")), p -> { });
        JsonNode body = server.chatRequests.get(0);
        assertThat(body.path("model").asText()).isEqualTo("chat-model");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("content").asText()).startsWith("hello");
        JsonNode options = body.path("options");
        assertThat(options.path("temperature").asDouble()).isZero();
        assertThat(options.path("seed").asInt()).isEqualTo(42);
        assertThat(options.path("num_ctx").asInt()).isEqualTo(4096);
        assertThat(options.path("num_predict").asInt()).isEqualTo(350);
    }

    @Test
    void unicodeSurvivesTheRoundTrip() throws Exception {
        String full = client().chat(List.of(ChatMessage.user("CONTEXT:\n[1] (a.md)\nकीमत ₹ 4,999 café\n\nQUESTION: कीमत")), p -> { });
        assertThat(full).contains("₹ 4,999").contains("café");
    }

    @Test
    void aMissingModelGetsAHelpfulMessage() {
        server.chatStatus = 404;
        server.chatError = "{\"error\":\"model 'chat-model' not found\"}";
        assertThatThrownBy(() -> client().chat(List.of(ChatMessage.user("hi")), p -> { }))
                .isInstanceOf(OllamaException.class).hasMessageContaining("not installed").hasMessageContaining("ollama pull chat-model");
    }

    @Test
    void otherHttpErrorsShowStatusAndDetail() {
        server.chatStatus = 500;
        server.chatError = "{\"error\":\"out of memory\"}";
        assertThatThrownBy(() -> client().chat(List.of(ChatMessage.user("hi")), p -> { }))
                .isInstanceOf(OllamaException.class).hasMessageContaining("500").hasMessageContaining("out of memory");
    }

    @Test
    void anErrorInTheMiddleOfTheStreamIsReported() {
        server.errorInStream = true;
        assertThatThrownBy(() -> client().chat(List.of(ChatMessage.user("CONTEXT:\n[1] (a.md)\nx y z\n\nQUESTION: x")), p -> { }))
                .isInstanceOf(OllamaException.class).hasMessageContaining("the model crashed");
    }

    @Test
    void aServerThatIsNotRunningIsExplained() {
        OllamaClient down = client("http://127.0.0.1:1", 16, 5);
        assertThatThrownBy(() -> down.chat(List.of(ChatMessage.user("hi")), p -> { }))
                .isInstanceOf(OllamaException.class).hasMessageContaining("not running");
        assertThatThrownBy(() -> down.embedQuery("hi")).isInstanceOf(OllamaException.class).hasMessageContaining("not running");
        assertThatThrownBy(down::installedModels).isInstanceOf(OllamaException.class).hasMessageContaining("not running");
    }

    @Test
    void aSlowModelTimesOutWithAClearMessage() {
        server.chatDelayMillis = 3000;
        assertThatThrownBy(() -> client(server.url(), 16, 1).chat(List.of(ChatMessage.user("hi")), p -> { }))
                .isInstanceOf(OllamaException.class).hasMessageContaining("longer than 1 seconds");
    }

    @Test
    void abandoningAnAnswerFromTheCallbackStopsReading() {
        assertThatThrownBy(() -> client().chat(List.of(ChatMessage.user("CONTEXT:\n[1] (a.md)\none two three four five six\n\nQUESTION: one two")),
                piece -> {
                    throw new IllegalStateException("stop");
                })).isInstanceOf(IllegalStateException.class).hasMessage("stop");
    }
}
