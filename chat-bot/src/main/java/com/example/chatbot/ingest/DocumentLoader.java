package com.example.chatbot.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads a company file into sections that keep their heading path. Markdown headings become paths such as
 * {@code Support > Response times}, so a passage is always found and understood together with where it belongs.
 * Supported: Markdown, plain text and PDF with a text layer (scanned PDFs need OCR first).
 */
public final class DocumentLoader {

    public static final Set<String> SUPPORTED = Set.of("md", "markdown", "txt", "pdf");
    static final long MAX_BYTES = 25L * 1024 * 1024;

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");

    public static boolean supports(Path file) {
        return SUPPORTED.contains(extension(file));
    }

    public List<Section> load(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) {
            throw new IOException("the file is larger than " + (MAX_BYTES / 1024 / 1024) + " MB");
        }
        String title = baseName(file);
        return switch (extension(file)) {
            case "md", "markdown" -> markdown(title, Files.readString(file, StandardCharsets.UTF_8));
            case "txt" -> plain(title, Files.readString(file, StandardCharsets.UTF_8));
            case "pdf" -> pdf(title, file);
            default -> throw new IOException("unsupported file type");
        };
    }

    static List<Section> markdown(String title, String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String[] path = new String[6];
        List<Section> sections = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        boolean inFence = false;
        for (String line : lines) {
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inFence = !inFence;
            }
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                flush(sections, title, path, body);
                int level = m.group(1).length();
                path[level - 1] = m.group(2).trim();
                Arrays.fill(path, level, path.length, null);
            } else {
                body.append(line).append('\n');
            }
        }
        flush(sections, title, path, body);
        return sections;
    }

    private static void flush(List<Section> sections, String title, String[] path, StringBuilder body) {
        String text = body.toString().strip();
        body.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String p : path) {
            if (p != null && !p.isBlank()) {
                parts.add(p);
            }
        }
        sections.add(new Section(parts.isEmpty() ? title : String.join(" > ", parts), text, 0));
    }

    static List<Section> plain(String title, String text) {
        String clean = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        return clean.isEmpty() ? List.of() : List.of(new Section(title, clean, 0));
    }

    private static List<Section> pdf(String title, Path file) throws IOException {
        List<Section> sections = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).replace("\r\n", "\n").strip();
                if (!text.isEmpty()) {
                    sections.add(new Section(title + " (page " + page + ")", text, page));
                }
            }
        }
        return sections;
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
