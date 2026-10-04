package com.example.chatbot.ingest;

import com.example.chatbot.chat.InjectionGuard;
import com.example.chatbot.config.ChatBotProperties;
import com.example.chatbot.index.Chunk;
import com.example.chatbot.index.IndexHolder;
import com.example.chatbot.index.IndexStorage;
import com.example.chatbot.index.Snapshot;
import com.example.chatbot.llm.Embedder;
import com.example.chatbot.llm.OllamaException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * "Trains" the chatbot: reads the company's files, cuts them into passages, embeds them and builds the search index.
 * It is incremental. A file is identified by the SHA-256 of its content, so unchanged files keep their existing passages
 * and vectors, only new or edited files are embedded again, and deleted files disappear from the index.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ChatBotProperties props;
    private final IndexHolder holder;
    private final Embedder embedder;
    private final IndexStorage storage = new IndexStorage();
    private final DocumentLoader loader = new DocumentLoader();
    private final Chunker chunker = new Chunker();
    private volatile IngestReport lastReport;

    public IngestService(ChatBotProperties props, IndexHolder holder, Embedder embedder) {
        this.props = props;
        this.holder = holder;
        this.embedder = embedder;
    }

    /** Loads the stored index from disk, if there is a usable one. */
    public void restore() {
        Snapshot stored = storage.load(props.indexDir());
        if (stored != null) {
            holder.set(stored);
            log.info("Restored the stored index: {} passages from {} files", stored.chunks().size(), stored.manifest().size());
        }
    }

    /** Scans the data folder and brings the index up to date. One run at a time. */
    public synchronized IngestReport ingest() {
        long started = System.nanoTime();
        List<String> warnings = new ArrayList<>();
        Path dataDir = props.dataDir();
        Snapshot current = holder.get();
        boolean sameModel = current.embedModel().equals(props.ollama().embedModel());

        Map<String, Path> files = findFiles(dataDir, warnings);
        Map<String, List<Integer>> existingBySource = indexBySource(current);

        List<Chunk> chunks = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        Map<String, String> manifest = new LinkedHashMap<>();
        int added = 0;
        int updated = 0;
        int unchanged = 0;
        boolean embeddingFailed = false;

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String name = entry.getKey();
            String hash;
            try {
                hash = sha256(entry.getValue());
            } catch (IOException e) {
                warnings.add(name + ": could not be read (" + e.getMessage() + ")");
                continue;
            }
            List<Integer> old = existingBySource.get(name);
            boolean known = old != null && hash.equals(current.manifest().get(name));
            if (known && sameModel && hasAllVectors(current, old)) {
                for (int i : old) {
                    chunks.add(current.chunks().get(i));
                    vectors.add(current.vectors().vector(i));
                }
                manifest.put(name, hash);
                unchanged++;
                continue;
            }

            List<Chunk> fresh;
            try {
                fresh = chunksOf(name, entry.getValue());
            } catch (IOException | RuntimeException e) {
                warnings.add(name + ": skipped, could not be read as a document (" + e.getMessage() + ")");
                continue;
            }
            if (fresh.isEmpty()) {
                warnings.add(name + ": no text found (a scanned PDF needs OCR first)");
                continue;
            }
            if (props.retrieval().screenDocuments()) {
                fresh = screen(name, fresh, warnings);
                if (fresh.isEmpty()) {
                    continue; // every passage was left out; the warnings say why
                }
            }
            List<float[]> freshVectors = new ArrayList<>();
            try {
                freshVectors.addAll(embedder.embedDocuments(fresh.stream().map(Chunk::text).toList()));
            } catch (OllamaException e) {
                embeddingFailed = true;
                warnings.add(name + ": indexed for keyword search only, " + e.getMessage());
                fresh.forEach(c -> freshVectors.add(null));
            }
            chunks.addAll(fresh);
            vectors.addAll(freshVectors);
            manifest.put(name, hash);
            if (old == null) {
                added++;
            } else {
                updated++;
            }
        }

        int removed = (int) current.manifest().keySet().stream().filter(n -> !manifest.containsKey(n) && !files.containsKey(n)).count();
        Snapshot next = new Snapshot(chunks, vectors.toArray(new float[0][]), manifest, props.ollama().embedModel());
        holder.set(next);
        try {
            storage.save(props.indexDir(), next);
        } catch (IOException e) {
            warnings.add("The index could not be saved to disk (" + e.getMessage() + "); it will be rebuilt on the next start");
        }
        IngestReport report = new IngestReport(files.size(), added, updated, unchanged, removed, chunks.size(),
                next.vectorsComplete() && !embeddingFailed, List.copyOf(warnings), (System.nanoTime() - started) / 1_000_000);
        log.info("Indexing done in {} ms: {} files ({} new, {} changed, {} unchanged, {} removed), {} passages, vectors complete: {}",
                report.millis(), report.files(), added, updated, unchanged, removed, report.chunks(), report.vectorsComplete());
        warnings.forEach(w -> log.warn("Indexing: {}", w));
        lastReport = report;
        return report;
    }

    /** The result of the most recent indexing run in this process, or null if none has run yet. */
    public IngestReport lastReport() {
        return lastReport;
    }

    /** Leaves out passages that look like orders to an AI (prompt injection) and says which, so a person can review them. */
    private static List<Chunk> screen(String name, List<Chunk> chunks, List<String> warnings) {
        List<Chunk> kept = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            var sign = InjectionGuard.suspiciousPassage(chunk.text());
            if (sign.isPresent()) {
                warnings.add(name + " (" + chunk.title() + "): left out of the index because it looks like an instruction aimed at an AI (\""
                        + sign.get() + "\"). Review the file; reword it if this is ordinary text.");
            } else {
                kept.add(chunk);
            }
        }
        return kept;
    }

    private List<Chunk> chunksOf(String name, Path file) throws IOException {
        List<Section> sections = loader.load(file);
        List<Chunker.Draft> drafts = chunker.chunk(sections, props.retrieval().chunkChars(), props.retrieval().chunkOverlap());
        List<Chunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            chunks.add(new Chunk(name + "#" + (i + 1), name, drafts.get(i).title(), drafts.get(i).text()));
        }
        return chunks;
    }

    /** Supported files under the data folder, keyed by their forward-slash path relative to it, sorted by name. */
    private Map<String, Path> findFiles(Path dataDir, List<String> warnings) {
        Map<String, Path> files = new TreeMap<>();
        if (!Files.isDirectory(dataDir)) {
            warnings.add("The data folder " + dataDir.toAbsolutePath() + " does not exist");
            return files;
        }
        try (Stream<Path> walk = Files.walk(dataDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = dataDir.relativize(p).toString().replace('\\', '/');
                String fileName = p.getFileName().toString();
                if (fileName.startsWith(".") || fileName.equalsIgnoreCase("README.md")) {
                    return;
                }
                if (DocumentLoader.supports(p)) {
                    files.put(name, p);
                } else {
                    warnings.add(name + ": ignored, unsupported type (supported: " + String.join(", ", DocumentLoader.SUPPORTED.stream().sorted().toList()) + ")");
                }
            });
        } catch (IOException e) {
            warnings.add("The data folder could not be read: " + e.getMessage());
        }
        return files;
    }

    private static Map<String, List<Integer>> indexBySource(Snapshot snapshot) {
        Map<String, List<Integer>> map = new HashMap<>();
        for (int i = 0; i < snapshot.chunks().size(); i++) {
            map.computeIfAbsent(snapshot.chunks().get(i).source(), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    private static boolean hasAllVectors(Snapshot snapshot, List<Integer> indexes) {
        for (int i : indexes) {
            if (snapshot.vectors().vector(i) == null) {
                return false;
            }
        }
        return true;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file))).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
