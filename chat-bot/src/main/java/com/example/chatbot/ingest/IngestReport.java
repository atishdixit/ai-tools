package com.example.chatbot.ingest;

import java.util.List;

/**
 * What an indexing run did.
 *
 * @param files           supported files found in the data folder
 * @param added           new files indexed
 * @param updated         changed files indexed again
 * @param unchanged       files whose existing passages and vectors were reused
 * @param removed         files that disappeared and were dropped from the index
 * @param chunks          passages in the index now
 * @param vectorsComplete false if some passages could not be embedded (the model was unavailable)
 * @param warnings        anything the user should know: ignored files, unreadable files, embedding problems
 */
public record IngestReport(
        int files,
        int added,
        int updated,
        int unchanged,
        int removed,
        int chunks,
        boolean vectorsComplete,
        List<String> warnings,
        long millis) {
}
