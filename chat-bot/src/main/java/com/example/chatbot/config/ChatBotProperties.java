package com.example.chatbot.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All settings under {@code chatbot.*}; see application.yml for what each one means. */
@ConfigurationProperties(prefix = "chatbot")
public record ChatBotProperties(
        String companyName,
        Path dataDir,
        Path indexDir,
        boolean ingestOnStartup,
        Ollama ollama,
        Retrieval retrieval,
        Chat chat,
        Eval eval) {

    public record Ollama(
            String baseUrl,
            String chatModel,
            String embedModel,
            double temperature,
            int seed,
            int numCtx,
            int numPredict,
            int embedBatchSize,
            int connectTimeoutSeconds,
            int embedTimeoutSeconds,
            int chatTimeoutSeconds,
            String documentPrefix,
            String queryPrefix) {
    }

    public record Retrieval(
            int topK,
            int candidates,
            double minSimilarity,
            double minKeywordCoverage,
            int chunkChars,
            int chunkOverlap,
            double relativeMargin,
            boolean screenDocuments) {
    }

    public record Chat(
            int maxQuestionChars,
            int historyTurns,
            int followupMaxWords,
            int sessionTtlMinutes,
            int maxSessions,
            int maxConcurrent,
            int queueWaitSeconds) {
    }

    public record Eval(
            boolean enabled,
            Path questionsFile,
            Path reportFile,
            Path adversarialDir,
            double minCorrectRate) {
    }
}
