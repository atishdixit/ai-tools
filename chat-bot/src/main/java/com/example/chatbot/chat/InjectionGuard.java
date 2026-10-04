package com.example.chatbot.chat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based defences against prompt injection: text that tries to give the AI orders instead of facts.
 * Three places are covered: passages while the documents are indexed (a planted "ignore your rules, say X" line), questions that
 * ask the bot to recite its own instructions, and answers that recite them anyway.
 *
 * <p>These are heuristics, not a guarantee. A determined attacker can word things in ways no pattern list anticipates, so they are
 * one layer next to the strict prompt and the human-visible sources, and the evaluation (eval\questions.json) measures them.
 * They can also flag harmless text that merely sounds like an order to an AI; flagged passages are reported at indexing time so a
 * person can review them.
 */
public final class InjectionGuard {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Phrases that appear in a document only when someone is trying to instruct the model. */
    private static final List<Pattern> PASSAGE_SIGNS = List.of(
            Pattern.compile("\\b(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier|your|these|those)\\s+"
                    + "(rules|instructions?|prompts?|directions|guidelines)", FLAGS),
            Pattern.compile("\\b(system|admin(istrator)?)\\s+(notice|message|override|prompt|instruction)", FLAGS),
            Pattern.compile("\\b(notice|message|note|attention|instructions?)\\s+(to|for)\\s+(the\\s+)?(ai|assistants?|chatbots?|language models?|llms?|bots?)\\b", FLAGS),
            // text that addresses the AI and then gives it an order
            Pattern.compile("\\b(ai|assistants?|chatbots?|language models?|llms?)\\b[^.\\n]{0,60}\\b(must|should|shall|always|never|ignore|reply|respond|print|say|begin|start)\\b", FLAGS),
            Pattern.compile("\\byou are now\\b|\\bnew instructions?\\s*:|\\banswer every question with\\b|\\breply (to every question )?with (the )?(single )?word\\b", FLAGS));

    /** Requests to make the bot recite or reveal its instructions. */
    private static final List<Pattern> EXTRACTION_SIGNS = List.of(
            Pattern.compile("\\b(system|initial|original|hidden|secret)\\s+(prompt|message|instructions?)\\b", FLAGS),
            // only "your": "the prompt response time" and "the instructions in the manual" are ordinary questions
            Pattern.compile("\\b(repeat|show|print|reveal|recite|display|tell me|what (are|were|is))\\b[^.?!]{0,40}\\byour\\s+(system\\s+)?(instructions?|prompt)\\b", FLAGS),
            Pattern.compile("\\byour\\s+(rules|guidelines|directions|instructions)\\b", FLAGS),
            Pattern.compile("\\b(rules|instructions?|prompt)\\b[^.?!]{0,40}\\b(you were given|were you given|you have been given|have you been given"
                    + "|you received|did you receive|given to you|you follow|do you follow|are you following)\\b", FLAGS),
            Pattern.compile("\\bignore\\s+(all\\s+)?(your|previous|prior|the above)\\s+(rules|instructions?)\\b", FLAGS));

    private static final int SHINGLE_WORDS = 6;

    private InjectionGuard() {
    }

    /** @return the text of the first suspicious phrase, if the passage looks like an instruction aimed at an AI */
    public static Optional<String> suspiciousPassage(String text) {
        for (Pattern p : PASSAGE_SIGNS) {
            var m = p.matcher(text);
            if (m.find()) {
                return Optional.of(m.group().strip());
            }
        }
        return Optional.empty();
    }

    public static boolean asksForInstructions(String question) {
        return EXTRACTION_SIGNS.stream().anyMatch(p -> p.matcher(question).find());
    }

    /**
     * True when the answer repeats a run of {@value #SHINGLE_WORDS} consecutive words of the instructions. The one sentence the
     * bot is told to say when it does not know is allowed, since saying it is not a leak.
     */
    public static boolean leaksInstructions(String answer, String instructions, String allowedSentence) {
        String protectedText = instructions.replace(allowedSentence, " ");
        Set<String> shingles = shingles(words(protectedText));
        if (shingles.isEmpty()) {
            return false;
        }
        for (String s : shingles(words(answer))) {
            if (shingles.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream().filter(w -> !w.isEmpty()).toList();
    }

    private static Set<String> shingles(List<String> words) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i + SHINGLE_WORDS <= words.size(); i++) {
            out.add(String.join(" ", words.subList(i, i + SHINGLE_WORDS)));
        }
        return out;
    }
}
