package com.example.chatbot.llm;

import java.util.List;
import java.util.function.Consumer;

/** A language model that answers a conversation, delivering the text piece by piece as it is produced. */
public interface ChatModel {

    /**
     * @param onToken called with each piece of text as it arrives; throw from it to abandon the answer
     * @return the complete answer
     */
    String chat(List<ChatMessage> messages, Consumer<String> onToken) throws OllamaException;
}
