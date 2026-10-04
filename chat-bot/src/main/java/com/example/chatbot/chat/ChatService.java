package com.example.chatbot.chat;

import com.example.chatbot.chat.Answer.Mode;
import com.example.chatbot.chat.Answer.SourceRef;
import com.example.chatbot.config.ChatBotProperties;
import com.example.chatbot.index.HybridRetriever;
import com.example.chatbot.index.HybridRetriever.Hit;
import com.example.chatbot.index.IndexHolder;
import com.example.chatbot.index.Snapshot;
import com.example.chatbot.llm.ChatMessage;
import com.example.chatbot.llm.ChatModel;
import com.example.chatbot.llm.Embedder;
import com.example.chatbot.llm.OllamaException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers one question:
 * <ol>
 *   <li>work out what to search for (a short follow-up is searched together with the previous question);</li>
 *   <li>find the best passages (meaning-based plus keyword search, merged);</li>
 *   <li><b>relevance gate:</b> if nothing is similar enough, refuse without calling the model at all;</li>
 *   <li>otherwise ask the model to answer from those passages only, streaming the text as it is produced;</li>
 *   <li>if the model is unavailable, show the most relevant passages as they are instead of failing.</li>
 * </ol>
 */
@Service
public class ChatService {

    /** Thrown for a question that cannot be processed; the message is shown to the user. */
    public static class InvalidQuestionException extends RuntimeException {
        public InvalidQuestionException(String message) {
            super(message);
        }
    }

    /** Thrown when too many questions are already being answered. */
    public static class BusyException extends RuntimeException {
        public BusyException(String message) {
            super(message);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");
    /** Words that make a short question depend on the one before: a leading "and/also/but/so/then/or", "what about", or a pronoun. */
    private static final Pattern FOLLOW_UP_CUE = Pattern.compile(
            "^\\s*(and|also|but|so|then|or)\\b|\\b(what|how) about\\b|\\b(it|its|they|them|their|that|this|those|these|he|she|his|her|there|same)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final int EXCERPT_CHARS = 320;

    private final ChatBotProperties props;
    private final IndexHolder holder;
    private final Embedder embedder;
    private final ChatModel model;
    private final SessionStore sessions;
    private final HybridRetriever retriever = new HybridRetriever();
    private final Semaphore slots;

    public ChatService(ChatBotProperties props, IndexHolder holder, Embedder embedder, ChatModel model, SessionStore sessions) {
        this.props = props;
        this.holder = holder;
        this.embedder = embedder;
        this.model = model;
        this.sessions = sessions;
        this.slots = new Semaphore(Math.max(1, props.chat().maxConcurrent()), true);
    }

    public Answer ask(String sessionId, String question) {
        return ask(sessionId, question, token -> { });
    }

    /** @param onToken receives the answer text piece by piece while the model writes it */
    public Answer ask(String sessionId, String rawQuestion, Consumer<String> onToken) {
        String question = validate(rawQuestion);
        String session = sessions.resolveId(sessionId);
        acquire();
        try {
            return answer(session, question, onToken);
        } finally {
            slots.release();
        }
    }

    private Answer answer(String session, String question, Consumer<String> onToken) {
        List<Turn> history = sessions.history(session);
        Snapshot snapshot = holder.get();
        List<String> notes = new ArrayList<>();

        if (snapshot.isEmpty()) {
            String text = "No company documents have been indexed yet. Add files to the data folder and re-index.";
            onToken.accept(text);
            return new Answer(session, text, Mode.NO_DOCUMENTS, List.of(), List.of(), notes, null, 0, 0);
        }

        if (InjectionGuard.asksForInstructions(question)) {
            log.info("A request to reveal the instructions was declined");
            onToken.accept(PromptBuilder.REFUSAL);
            sessions.add(session, new Turn(question, PromptBuilder.REFUSAL));
            return new Answer(session, PromptBuilder.REFUSAL, Mode.NO_ANSWER, List.of(), List.of(), notes, null, 0, 0);
        }

        long t0 = System.nanoTime();
        String searchQuery = searchQuery(question, history);
        float[] queryVector = null;
        if (snapshot.hasVectors()) {
            try {
                queryVector = embedder.embedQuery(searchQuery);
            } catch (OllamaException e) {
                log.warn("Question could not be embedded; using keyword search only: {}", e.getMessage());
                notes.add("Meaning-based search is unavailable (" + e.getMessage() + "); keyword search was used.");
            }
        } else {
            notes.add("The index has no vectors yet; keyword search was used.");
        }
        HybridRetriever.Result result = retriever.retrieve(snapshot, searchQuery, queryVector,
                props.retrieval().topK(), props.retrieval().candidates());
        long retrieveMillis = (System.nanoTime() - t0) / 1_000_000;

        List<SourceRef> retrieved = refs(result.hits());
        Double top = Double.isNaN(result.bestCosine()) ? null : result.bestCosine();

        if (!relevantEnough(result)) {
            onToken.accept(PromptBuilder.REFUSAL);
            sessions.add(session, new Turn(question, PromptBuilder.REFUSAL));
            return new Answer(session, PromptBuilder.REFUSAL, Mode.NO_ANSWER, List.of(), retrieved, notes, top, retrieveMillis, 0);
        }

        long t1 = System.nanoTime();
        List<Hit> context = trim(result.hits());
        List<ChatMessage> prompt = PromptBuilder.build(props.companyName(), context, history, question);
        try {
            String text = model.chat(prompt, onToken).strip();
            long generateMillis = (System.nanoTime() - t1) / 1_000_000;
            if (text.isEmpty()) {
                return extractive(session, question, context, retrieved, notes, top, retrieveMillis, "The model returned an empty answer.", onToken);
            }
            if (isRefusal(text)) {
                sessions.add(session, new Turn(question, PromptBuilder.REFUSAL));
                return new Answer(session, text, Mode.NO_ANSWER, List.of(), retrieved, notes, top, retrieveMillis, generateMillis);
            }
            if (InjectionGuard.leaksInstructions(text, PromptBuilder.systemPrompt(props.companyName()), PromptBuilder.REFUSAL)) {
                log.warn("An answer that repeated the instructions was replaced by a refusal");
                sessions.add(session, new Turn(question, PromptBuilder.REFUSAL));
                return new Answer(session, PromptBuilder.REFUSAL, Mode.NO_ANSWER, List.of(), retrieved, notes, top, retrieveMillis, generateMillis);
            }
            sessions.add(session, new Turn(question, text));
            return new Answer(session, text, Mode.ANSWER, cited(text, context), retrieved, notes, top, retrieveMillis, generateMillis);
        } catch (OllamaException e) {
            log.warn("The language model is unavailable: {}", e.getMessage());
            return extractive(session, question, context, retrieved, notes, top, retrieveMillis, e.getMessage(), onToken);
        }
    }

    /**
     * Sends the model only passages close to the best match. Every extra passage costs reading time (about 23 tokens a second on
     * a laptop CPU) and gives the small model something to be distracted by; the top passage is always kept.
     */
    List<Hit> trim(List<Hit> hits) {
        double margin = props.retrieval().relativeMargin();
        if (margin <= 0 || hits.size() <= 1) {
            return hits;
        }
        double best = hits.stream().mapToDouble(Hit::cosine).filter(c -> !Double.isNaN(c)).max().orElse(Double.NaN);
        if (Double.isNaN(best)) {
            return hits; // keyword-only search: no similarity to compare
        }
        List<Hit> kept = new ArrayList<>();
        kept.add(hits.get(0));
        for (Hit hit : hits.subList(1, hits.size())) {
            if (Double.isNaN(hit.cosine()) || hit.cosine() >= best - margin) {
                kept.add(hit);
            }
        }
        return kept;
    }

    /** The relevance gate: refuse early when nothing in the index resembles the question. */
    boolean relevantEnough(HybridRetriever.Result result) {
        if (result.hits().isEmpty()) {
            return false;
        }
        if (result.vectorsUsed()) {
            return result.bestCosine() >= props.retrieval().minSimilarity();
        }
        return result.bestCoverage() >= props.retrieval().minKeywordCoverage();
    }

    /**
     * A short follow-up ("and the Pro plan?", "what about Growth?", "is it free?") has no meaning alone, so it is searched together
     * with the previous question. A short question that merely follows another one ("what is the capital of France?") is NOT merged:
     * that would make an unrelated question look company-related, and drag in passages from the old topic.
     */
    String searchQuery(String question, List<Turn> history) {
        if (history.isEmpty() || wordCount(question) > props.chat().followupMaxWords() || !FOLLOW_UP_CUE.matcher(question).find()) {
            return question;
        }
        return history.get(history.size() - 1).question() + " " + question;
    }

    private Answer extractive(String session, String question, List<Hit> context, List<SourceRef> retrieved,
                              List<String> notes, Double top, long retrieveMillis, String reason, Consumer<String> onToken) {
        StringBuilder text = new StringBuilder("The language model is not available, so here are the most relevant passages "
                + "from the company documents:\n");
        List<Hit> shown = context.subList(0, Math.min(2, context.size()));
        for (Hit hit : shown) {
            text.append("\n").append(hit.chunk().text().strip()).append("\n(").append(hit.chunk().source()).append(")\n");
        }
        notes.add(reason);
        String body = text.toString().stripTrailing();
        onToken.accept(body);
        return new Answer(session, body, Mode.EXTRACTIVE, refs(shown), retrieved, notes, top, retrieveMillis, 0);
    }

    /** The passages the model cited with [n]; if it cited none that exist, all the passages it was given. */
    List<SourceRef> cited(String answer, List<Hit> hits) {
        Set<Integer> numbers = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= hits.size()) {
                numbers.add(n);
            }
        }
        if (numbers.isEmpty()) {
            return refs(hits);
        }
        List<SourceRef> out = new ArrayList<>();
        for (int n : numbers) {
            out.add(ref(n, hits.get(n - 1)));
        }
        return out;
    }

    private static List<SourceRef> refs(List<Hit> hits) {
        List<SourceRef> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            out.add(ref(i + 1, hits.get(i)));
        }
        return out;
    }

    private static SourceRef ref(int n, Hit hit) {
        String text = hit.chunk().text();
        int bodyStart = text.indexOf('\n') + 1;
        String body = text.substring(Math.max(0, bodyStart)).replaceAll("\\s+", " ").strip();
        String excerpt = body.length() <= EXCERPT_CHARS ? body : body.substring(0, EXCERPT_CHARS).stripTrailing() + "...";
        double score = Double.isNaN(hit.cosine()) ? hit.keywordScore() : hit.cosine();
        return new SourceRef(n, hit.chunk().source(), hit.chunk().title(), Math.round(score * 1000) / 1000.0, excerpt);
    }

    static boolean isRefusal(String text) {
        return text.toLowerCase().replace('’', '\'').startsWith("i don't have that information");
    }

    private String validate(String question) {
        if (question == null || question.isBlank()) {
            throw new InvalidQuestionException("Please type a question.");
        }
        String q = question.strip();
        if (q.length() > props.chat().maxQuestionChars()) {
            throw new InvalidQuestionException("The question is too long (" + q.length() + " characters; the limit is "
                    + props.chat().maxQuestionChars() + ").");
        }
        return q;
    }

    private void acquire() {
        try {
            if (!slots.tryAcquire(props.chat().queueWaitSeconds(), TimeUnit.SECONDS)) {
                throw new BusyException("The assistant is busy answering other questions. Please try again in a moment.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusyException("Interrupted while waiting for a free slot.");
        }
    }

    private static int wordCount(String text) {
        String t = text.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }
}
