package com.example.chatbot.ingest;

/**
 * A part of a document with its place in the document's outline.
 *
 * @param heading e.g. {@code Products > Pro plan} for Markdown, or {@code handbook (page 3)} for a PDF
 * @param text    the body text under that heading
 * @param page    1-based page for PDFs, 0 otherwise
 */
public record Section(String heading, String text, int page) {
}
