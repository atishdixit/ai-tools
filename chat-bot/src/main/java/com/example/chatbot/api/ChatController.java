package com.example.chatbot.api;

import com.example.chatbot.api.Dtos.ChatRequest;
import com.example.chatbot.api.Dtos.DocumentInfo;
import com.example.chatbot.api.Dtos.Status;
import com.example.chatbot.chat.Answer;
import com.example.chatbot.chat.ChatService;
import com.example.chatbot.config.ChatBotProperties;
import com.example.chatbot.index.IndexHolder;
import com.example.chatbot.index.Snapshot;
import com.example.chatbot.ingest.IngestReport;
import com.example.chatbot.ingest.IngestService;
import com.example.chatbot.llm.OllamaClient;
import com.example.chatbot.llm.OllamaException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ChatService chat;
    private final IngestService ingest;
    private final IndexHolder holder;
    private final OllamaClient ollama;
    private final ChatBotProperties props;
    private final ExecutorService streamExecutor;

    public ChatController(ChatService chat, IngestService ingest, IndexHolder holder, OllamaClient ollama,
                          ChatBotProperties props, ExecutorService streamExecutor) {
        this.chat = chat;
        this.ingest = ingest;
        this.holder = holder;
        this.ollama = ollama;
        this.props = props;
        this.streamExecutor = streamExecutor;
    }

    /** Ask a question and get the complete answer in one response. */
    @PostMapping("/chat")
    public Answer ask(@RequestBody ChatRequest request) {
        return chat.ask(request.sessionId(), request.question());
    }

    /**
     * Ask a question and receive the answer as server-sent events: {@code token} events with each piece of text as the
     * model writes it, then one {@code done} event with the full {@link Answer} (sources included), or an {@code error} event.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamExecutor.execute(() -> {
            try {
                Answer answer = chat.ask(request.sessionId(), request.question(), token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(Map.of("t", token), MediaType.APPLICATION_JSON));
                    } catch (IOException | IllegalStateException e) {
                        throw new CancellationException("The browser closed the connection");
                    }
                });
                emitter.send(SseEmitter.event().name("done").data(answer, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (CancellationException e) {
                log.debug("Answer abandoned: {}", e.getMessage());
            } catch (ChatService.InvalidQuestionException | ChatService.BusyException e) {
                fail(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("Streaming answer failed", e);
                fail(emitter, "Something went wrong while answering.");
            }
        });
        return emitter;
    }

    /** Re-reads the data folder and updates the index: the "training" step. */
    @PostMapping("/ingest")
    public IngestReport reindex() {
        return ingest.ingest();
    }

    @GetMapping("/status")
    public Status status() {
        Snapshot snapshot = holder.get();
        boolean up = false;
        String message = null;
        boolean chatInstalled = false;
        boolean embedInstalled = false;
        try {
            List<String> installed = ollama.installedModels();
            up = true;
            chatInstalled = ollama.isModelInstalled(installed, props.ollama().chatModel());
            embedInstalled = ollama.isModelInstalled(installed, props.ollama().embedModel());
            if (!chatInstalled) {
                message = "Model not installed. Run: ollama pull " + props.ollama().chatModel();
            } else if (!embedInstalled) {
                message = "Model not installed. Run: ollama pull " + props.ollama().embedModel();
            }
        } catch (OllamaException e) {
            message = e.getMessage();
        }
        return new Status(props.companyName(), up, message, props.ollama().chatModel(), chatInstalled, props.ollama().embedModel(),
                embedInstalled, snapshot.manifest().size(), snapshot.chunks().size(), snapshot.vectorsComplete(), ingest.lastReport());
    }

    @GetMapping("/documents")
    public List<DocumentInfo> documents() {
        return holder.get().chunksPerSource().entrySet().stream()
                .map(e -> new DocumentInfo(e.getKey(), e.getValue())).toList();
    }

    private static void fail(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            // the client is already gone
        }
        emitter.complete();
    }
}
