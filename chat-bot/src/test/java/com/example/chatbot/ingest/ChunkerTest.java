package com.example.chatbot.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private final Chunker chunker = new Chunker();

    private static Section section(String heading, String text) {
        return new Section(heading, text, 0);
    }

    @Test
    void everyPassageStartsWithItsHeadingPath() {
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("Pricing > Pro plan", "Rs 19,999 per month.")), 900, 120);
        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("Pricing > Pro plan");
            assertThat(d.text()).isEqualTo("Pricing > Pro plan\nRs 19,999 per month.");
        });
    }

    @Test
    void sectionsAreNeverMergedAcrossHeadings() {
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("A", "one short line"), section("B", "another short line")), 900, 120);
        assertThat(drafts).extracting(Chunker.Draft::title).containsExactly("A", "B");
    }

    @Test
    void smallParagraphsOfOneSectionArePackedTogether() {
        String text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        assertThat(chunker.chunk(List.of(section("S", text)), 900, 120)).hasSize(1);
    }

    @Test
    void aFullPassageStartsAnotherOne() {
        String p = "word ".repeat(100).strip(); // 499 chars
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("S", p + "\n\n" + p + "\n\n" + p)), 900, 0);
        assertThat(drafts).hasSize(3);
        assertThat(drafts).allSatisfy(d -> assertThat(d.text().length()).isLessThanOrEqualTo(900));
    }

    @Test
    void aTableIsKeptInOnePiece() {
        String table = "| Plan | Price |\n|---|---|\n| Starter | Rs 4,999 |\n| Growth | Rs 14,999 |\n| Enterprise | custom |";
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("Pricing", table)), 900, 120);
        assertThat(drafts).singleElement().satisfies(d -> assertThat(d.text()).contains("Starter").contains("Growth").contains("Enterprise"));
    }

    @Test
    void aVeryLongBlockIsSplitAtSentenceEndsWithinTheLimit() {
        String sentence = "This is a fairly ordinary sentence about the company and its products. ";
        String longBlock = sentence.repeat(40).strip();
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("Long", longBlock)), 500, 100);
        assertThat(drafts.size()).isGreaterThan(3);
        assertThat(drafts).allSatisfy(d -> {
            assertThat(d.text().length()).isLessThanOrEqualTo(500);
            assertThat(d.text()).startsWith("Long\n");
        });
    }

    @Test
    void splitPiecesOverlapSoABoundaryFactIsNotLost() {
        String text = "Alpha one is here. Beta two is here. Gamma three is here. Delta four is here. Epsilon five is here. Zeta six is here.";
        List<String> pieces = Chunker.splitLong(text, 60, 30);
        assertThat(pieces.size()).isGreaterThan(1);
        for (int i = 1; i < pieces.size(); i++) {
            String previousTail = pieces.get(i - 1).substring(pieces.get(i - 1).length() - 10);
            String lastWord = previousTail.substring(previousTail.lastIndexOf(' ') + 1);
            assertThat(pieces.get(i)).as("piece %d repeats the end of the one before", i).contains(lastWord);
        }
    }

    @Test
    void textWithoutSentenceEndsIsCutAtSpaces() {
        String text = "word ".repeat(300).strip();
        List<String> pieces = Chunker.splitLong(text, 100, 0);
        assertThat(pieces).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(100));
        assertThat(String.join(" ", pieces).replaceAll("\\s+", " ")).isEqualTo(text);
    }

    @Test
    void aSingleHugeTokenIsHardCut() {
        List<String> pieces = Chunker.splitLong("x".repeat(1000), 200, 0);
        assertThat(pieces).hasSize(5).allSatisfy(p -> assertThat(p).hasSize(200));
    }

    @Test
    void emptyAndBlankSectionsProduceNothing() {
        assertThat(chunker.chunk(List.of(), 900, 120)).isEmpty();
        assertThat(chunker.chunk(List.of(section("S", "   \n\n  ")), 900, 120)).isEmpty();
    }

    @Test
    void aTinyLimitIsRaisedToASaneMinimum() {
        List<Chunker.Draft> drafts = chunker.chunk(List.of(section("Heading", "A short body.")), 10, 5);
        assertThat(drafts).hasSize(1);
    }
}
