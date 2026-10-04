package com.example.chatbot.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentLoaderTest {

    @TempDir
    Path dir;

    private final DocumentLoader loader = new DocumentLoader();

    @Test
    void markdownHeadingsBecomeAPathAndNestingIsTracked() {
        String md = "# Support\n\nIntro text.\n\n## Response times\n\nP1 within 30 minutes.\n\n### Escalation\n\nTo the lead.\n\n## Contact\n\nEmail us.\n";
        List<Section> sections = DocumentLoader.markdown("file", md);
        assertThat(sections).extracting(Section::heading).containsExactly(
                "Support", "Support > Response times", "Support > Response times > Escalation", "Support > Contact");
        assertThat(sections.get(1).text()).isEqualTo("P1 within 30 minutes.");
    }

    @Test
    void aHeadingWithNoBodyProducesNoSection() {
        assertThat(DocumentLoader.markdown("f", "# Title\n\n## Empty\n\n## Real\n\nBody")).extracting(Section::heading).containsExactly("Title > Real");
    }

    @Test
    void textBeforeTheFirstHeadingBelongsToTheFileTitle() {
        List<Section> sections = DocumentLoader.markdown("notes", "Loose intro line.\n\n# Heading\n\nBody");
        assertThat(sections.get(0).heading()).isEqualTo("notes");
        assertThat(sections.get(0).text()).isEqualTo("Loose intro line.");
    }

    @Test
    void hashSignsInsideCodeFencesAreNotHeadings() {
        String md = "# Doc\n\n```\n# not a heading\necho hi\n```\n\nAfter.";
        List<Section> sections = DocumentLoader.markdown("f", md);
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).text()).contains("# not a heading").contains("After.");
    }

    @Test
    void windowsLineEndingsAreHandled() {
        List<Section> sections = DocumentLoader.markdown("f", "# A\r\n\r\nline one\r\n\r\n## B\r\n\r\nline two\r\n");
        assertThat(sections).extracting(Section::heading).containsExactly("A", "A > B");
        assertThat(sections.get(0).text()).doesNotContain("\r");
    }

    @Test
    void headingsKeepTheirTextExactlyIncludingSymbols() {
        assertThat(DocumentLoader.markdown("f", "## Pricing (INR) & billing ##\n\nBody").get(0).heading()).isEqualTo("Pricing (INR) & billing");
    }

    @Test
    void plainTextIsOneSectionNamedAfterTheFile() throws IOException {
        Path file = Files.writeString(dir.resolve("announcements.txt"), "Line one.\r\n\r\nLine two.\r\n");
        List<Section> sections = loader.load(file);
        assertThat(sections).singleElement().satisfies(s -> {
            assertThat(s.heading()).isEqualTo("announcements");
            assertThat(s.text()).isEqualTo("Line one.\n\nLine two.");
        });
    }

    @Test
    void emptyFilesGiveNoSections() throws IOException {
        assertThat(loader.load(Files.writeString(dir.resolve("empty.md"), ""))).isEmpty();
        assertThat(loader.load(Files.writeString(dir.resolve("blank.txt"), "  \n \n"))).isEmpty();
    }

    @Test
    void unicodeTextSurvives() throws IOException {
        Path file = Files.writeString(dir.resolve("hindi.md"), "# मूल्य\n\nकीमत Rs 4,999 ₹ — café\n");
        List<Section> sections = loader.load(file);
        assertThat(sections.get(0).heading()).isEqualTo("मूल्य");
        assertThat(sections.get(0).text()).contains("₹").contains("café");
    }

    @Test
    void pdfPagesBecomeSectionsWithPageNumbers() throws IOException {
        Path pdf = dir.resolve("handbook.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (String text : new String[] {"First page about travel.", "Second page about equipment."}) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        List<Section> sections = loader.load(pdf);
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("handbook (page 1)");
        assertThat(sections.get(0).page()).isEqualTo(1);
        assertThat(sections.get(1).text()).contains("equipment");
    }

    @Test
    void aPdfWithoutTextGivesNoSectionsRatherThanAnError() throws IOException {
        Path pdf = dir.resolve("scan.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        assertThat(loader.load(pdf)).isEmpty();
    }

    @Test
    void aCorruptPdfIsAnIoError() throws IOException {
        Path pdf = Files.writeString(dir.resolve("broken.pdf"), "this is not a pdf");
        assertThatThrownBy(() -> loader.load(pdf)).isInstanceOf(IOException.class);
    }

    @Test
    void supportedTypesAreRecognisedInAnyCase() {
        assertThat(DocumentLoader.supports(Path.of("a.MD"))).isTrue();
        assertThat(DocumentLoader.supports(Path.of("a.Txt"))).isTrue();
        assertThat(DocumentLoader.supports(Path.of("a.PDF"))).isTrue();
        assertThat(DocumentLoader.supports(Path.of("a.docx"))).isFalse();
        assertThat(DocumentLoader.supports(Path.of("noextension"))).isFalse();
    }
}
