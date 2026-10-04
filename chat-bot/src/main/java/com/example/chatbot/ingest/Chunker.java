package com.example.chatbot.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts sections into passages of at most {@code maxChars}. Rules, in order of importance:
 * <ol>
 *   <li>A passage never crosses a heading: a section stays together with its own title.</li>
 *   <li>Paragraphs, lists and tables are never cut in half unless one block alone is longer than the limit.</li>
 *   <li>Small paragraphs of the same section are packed together; an over-long block is split at sentence ends,
 *       with a little overlap so a fact on the boundary is not lost.</li>
 *   <li>Every passage starts with its heading path, so searching and embedding see the context.</li>
 * </ol>
 */
public final class Chunker {

    /** @param title heading path; @param text full passage text, beginning with the title */
    public record Draft(String title, String text) {
    }

    private static final int MIN_BODY = 200;

    public List<Draft> chunk(List<Section> sections, int maxChars, int overlap) {
        List<Draft> out = new ArrayList<>();
        for (Section section : sections) {
            String prefix = section.heading() + "\n";
            int limit = Math.max(MIN_BODY, maxChars - prefix.length());
            for (String body : pack(section.text(), limit, Math.min(overlap, limit / 2))) {
                out.add(new Draft(section.heading(), prefix + body));
            }
        }
        return out;
    }

    static List<String> pack(String text, int limit, int overlap) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : text.strip().split("\\n\\s*\\n")) {
            String paragraph = block.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (paragraph.length() > limit) {
                flush(pieces, current);
                pieces.addAll(splitLong(paragraph, limit, overlap));
            } else if (current.length() > 0 && current.length() + 2 + paragraph.length() > limit) {
                flush(pieces, current);
                current.append(paragraph);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(paragraph);
            }
        }
        flush(pieces, current);
        return pieces;
    }

    private static void flush(List<String> pieces, StringBuilder current) {
        if (current.length() > 0) {
            pieces.add(current.toString());
            current.setLength(0);
        }
    }

    /** Splits one over-long block at sentence ends (or lines), carrying a short tail into the next piece. */
    static List<String> splitLong(String block, int limit, int overlap) {
        List<String> units = new ArrayList<>();
        for (String unit : block.split("(?<=[.!?])\\s+|\\n")) {
            String u = unit.strip();
            if (u.isEmpty()) {
                continue;
            }
            while (u.length() > limit) { // a single "sentence" longer than the limit: cut at a space
                int cut = u.lastIndexOf(' ', limit);
                if (cut < limit / 2) {
                    cut = limit;
                }
                units.add(u.substring(0, cut).strip());
                u = u.substring(cut).strip();
            }
            if (!u.isEmpty()) {
                units.add(u);
            }
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + 1 + unit.length() > limit) {
                String finished = current.toString();
                pieces.add(finished);
                current.setLength(0);
                String tail = tail(finished, overlap);
                if (!tail.isEmpty() && tail.length() + 1 + unit.length() <= limit) {
                    current.append(tail).append(' ');
                }
            }
            if (current.length() > 0 && current.charAt(current.length() - 1) != ' ') {
                current.append(' ');
            }
            current.append(unit);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    /** The last sentence of {@code text} if it fits in {@code max} characters, else the last words that do. */
    static String tail(String text, int max) {
        if (max <= 0 || text.length() <= max) {
            return max <= 0 ? "" : text;
        }
        String candidate = text.substring(text.length() - max);
        int space = candidate.indexOf(' ');
        return space < 0 ? "" : candidate.substring(space + 1).strip();
    }
}
