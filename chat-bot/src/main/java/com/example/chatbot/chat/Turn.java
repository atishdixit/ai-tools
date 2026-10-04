package com.example.chatbot.chat;

/** One question and the answer it received; the unit of conversation memory. */
public record Turn(String question, String answer) {
}
