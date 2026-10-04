package com.example.chatbot.llm;

/** The model server could not do what was asked (not running, model missing, timeout, bad response). The message is user-readable. */
public class OllamaException extends Exception {

    public OllamaException(String message) {
        super(message);
    }

    public OllamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
