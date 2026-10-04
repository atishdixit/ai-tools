package com.example.chatbot.api;

import com.example.chatbot.ingest.IngestReport;

/** Request and response bodies of the REST API. */
public final class Dtos {

    private Dtos() {
    }

    /** @param sessionId optional; keep sending the one you were given to continue a conversation */
    public record ChatRequest(String sessionId, String question) {
    }

    public record DocumentInfo(String source, int passages) {
    }

    public record Status(
            String company,
            boolean ollamaUp,
            String ollamaMessage,
            String chatModel,
            boolean chatModelInstalled,
            String embedModel,
            boolean embedModelInstalled,
            int documents,
            int passages,
            boolean vectorsComplete,
            IngestReport lastIngest) {
    }
}
