package com.example.chatbot.index;

import com.example.chatbot.config.ChatBotProperties;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** The one place that knows the current index. Swapping is atomic, so readers always see a whole snapshot. */
@Component
public class IndexHolder {

    private final AtomicReference<Snapshot> current;

    public IndexHolder(ChatBotProperties props) {
        this.current = new AtomicReference<>(Snapshot.empty(props.ollama().embedModel()));
    }

    public Snapshot get() {
        return current.get();
    }

    public void set(Snapshot snapshot) {
        current.set(snapshot);
    }
}
