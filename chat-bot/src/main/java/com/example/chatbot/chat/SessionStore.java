package com.example.chatbot.chat;

import com.example.chatbot.config.ChatBotProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Remembers the last few turns of each conversation, in memory only. Sessions expire when idle and the number of
 * sessions is capped, so memory use stays bounded. Nothing is written to disk: conversations are not stored.
 */
@Component
public class SessionStore {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final Cache<String, List<Turn>> sessions;
    private final int maxTurns;

    public SessionStore(ChatBotProperties props) {
        this.maxTurns = props.chat().historyTurns();
        this.sessions = Caffeine.newBuilder()
                .maximumSize(props.chat().maxSessions())
                .expireAfterAccess(Duration.ofMinutes(props.chat().sessionTtlMinutes()))
                .build();
    }

    /** The client's id if it is well-formed, otherwise a new one (a client can never choose a malicious key). */
    public String resolveId(String requested) {
        return requested != null && VALID_ID.matcher(requested).matches() ? requested : UUID.randomUUID().toString();
    }

    public List<Turn> history(String sessionId) {
        List<Turn> turns = sessions.getIfPresent(sessionId);
        return turns == null ? List.of() : List.copyOf(turns);
    }

    public void add(String sessionId, Turn turn) {
        sessions.asMap().compute(sessionId, (id, existing) -> {
            List<Turn> turns = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            turns.add(turn);
            while (turns.size() > maxTurns) {
                turns.remove(0);
            }
            return turns;
        });
    }
}
