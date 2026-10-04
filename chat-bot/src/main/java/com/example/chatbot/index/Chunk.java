package com.example.chatbot.index;

/**
 * One searchable passage.
 *
 * @param id     stable id such as {@code products-and-pricing.md#3}
 * @param source the file the passage came from (path relative to the data folder)
 * @param title  where in the file it sits, e.g. {@code Pricing > Pro plan}
 * @param text   the passage, starting with its title so the heading is searched and embedded too
 */
public record Chunk(String id, String source, String title, String text) {
}
