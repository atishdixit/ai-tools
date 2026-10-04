package com.example.chatbot.eval;

import com.example.chatbot.chat.Answer;
import com.example.chatbot.chat.ChatService;
import com.example.chatbot.chat.SessionStore;
import com.example.chatbot.config.ChatBotProperties;
import com.example.chatbot.index.IndexHolder;
import com.example.chatbot.ingest.IngestService;
import com.example.chatbot.llm.ChatModel;
import com.example.chatbot.llm.Embedder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs every question in {@code eval/questions.json} through the real pipeline and the real local model, scores the
 * answers, writes {@code eval/report.md} and exits with 0 (all targets met) or 1. Started by {@code eval.bat}.
 * Options: {@code --chatbot.eval.split=dev|holdout|all} and {@code --chatbot.eval.only=D1,O3}.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "chatbot.eval", name = "enabled", havingValue = "true")
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final ChatBotProperties props;
    private final ChatService mainChat;
    private final IndexHolder mainIndex;
    private final Embedder embedder;
    private final ChatModel model;
    private final ConfigurableApplicationContext context;
    private final String splitFilter;
    private final String onlyIds;

    public EvalRunner(ChatBotProperties props, ChatService mainChat, IndexHolder mainIndex, Embedder embedder, ChatModel model,
                      ConfigurableApplicationContext context,
                      @Value("${chatbot.eval.split:all}") String splitFilter,
                      @Value("${chatbot.eval.only:}") String onlyIds) {
        this.props = props;
        this.mainChat = mainChat;
        this.mainIndex = mainIndex;
        this.embedder = embedder;
        this.model = model;
        this.context = context;
        this.splitFilter = splitFilter;
        this.onlyIds = onlyIds;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code;
        try {
            code = evaluate();
        } catch (Exception e) {
            log.error("The evaluation could not run: {}", e.toString());
            code = 2;
        }
        int exitCode = code;
        SpringApplication.exit(context, () -> exitCode);
        System.exit(exitCode);
    }

    private int evaluate() throws IOException {
        if (mainIndex.get().isEmpty() || !mainIndex.get().vectorsComplete()) {
            log.error("The index is empty or has no vectors. Is Ollama running with the embedding model installed? Run setup.bat.");
            return 2;
        }
        List<EvalQuestion> all = List.of(new ObjectMapper().readValue(props.eval().questionsFile().toFile(), EvalQuestion[].class));
        Set<String> only = onlyIds.isBlank() ? Set.of() : Set.of(onlyIds.split("\\s*,\\s*"));
        List<EvalQuestion> questions = all.stream()
                .filter(q -> splitFilter.equals("all") || splitFilter.equals(q.split()))
                .filter(q -> only.isEmpty() || only.contains(q.id()))
                .toList();
        log.info("Evaluating {} of {} questions (split={}) with {} + {}", questions.size(), all.size(), splitFilter,
                props.ollama().chatModel(), props.ollama().embedModel());

        Map<String, ChatService> services = new HashMap<>();
        services.put("main", mainChat);
        List<EvalReport.Result> results = new ArrayList<>();
        int n = 0;
        for (EvalQuestion q : questions) {
            n++;
            ChatService chat = services.computeIfAbsent(q.corpus() == null ? "main" : q.corpus(), this::serviceFor);
            String session = "eval" + UUID.randomUUID().toString().replace("-", "");
            for (String earlier : q.historyOrEmpty()) {
                chat.ask(session, earlier);
            }
            Answer answer = chat.ask(session, q.question());
            Scorer.Score score = Scorer.score(q, answer);
            results.add(new EvalReport.Result(q, answer, score));
            log.info("[{}/{}] {} {} {} ({} s)", n, questions.size(), score.correct() ? "PASS" : "FAIL", q.id(), q.question(),
                    String.format("%.1f", (answer.retrieveMillis() + answer.generateMillis()) / 1000.0));
        }

        EvalReport report = new EvalReport(props, results);
        Path file = props.eval().reportFile();
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, report.markdown(), StandardCharsets.UTF_8);
        report.printSummary(log);
        log.info("Report written to {}", file.toAbsolutePath());
        return report.meetsTargets(props.eval().minCorrectRate()) ? 0 : 1;
    }

    /** A second, separate chat pipeline over the poisoned documents, with its own throw-away index. */
    private ChatService serviceFor(String corpus) {
        try {
            Path data = corpus.equals("adversarial") ? props.eval().adversarialDir() : props.dataDir();
            ChatBotProperties p = new ChatBotProperties(props.companyName(), data, Files.createTempDirectory("chatbot-eval-"), false,
                    props.ollama(), props.retrieval(), props.chat(), props.eval());
            IndexHolder holder = new IndexHolder(p);
            IngestService ingest = new IngestService(p, holder, embedder);
            ingest.ingest();
            return new ChatService(p, holder, embedder, model, new SessionStore(p));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare the evaluation corpus '" + corpus + "'", e);
        }
    }
}
