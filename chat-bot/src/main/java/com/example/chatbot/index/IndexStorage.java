package com.example.chatbot.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves the index to disk so a restart does not have to embed every document again. Two files: {@code chunks.json}
 * (passages and the file manifest) and {@code vectors.bin} (the vectors). A damaged or mismatching index is ignored,
 * never trusted: the caller simply rebuilds it.
 */
public final class IndexStorage {

    private static final Logger log = LoggerFactory.getLogger(IndexStorage.class);
    private static final String CHUNKS = "chunks.json";
    private static final String VECTORS = "vectors.bin";
    private static final int FORMAT = 1;

    private record Meta(int format, String embedModel, Map<String, String> manifest, List<Chunk> chunks) {
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public void save(Path dir, Snapshot snapshot) throws IOException {
        Files.createDirectories(dir);
        Path chunksTmp = dir.resolve(CHUNKS + ".tmp");
        Path vectorsTmp = dir.resolve(VECTORS + ".tmp");
        mapper.writeValue(chunksTmp.toFile(), new Meta(FORMAT, snapshot.embedModel(), snapshot.manifest(), snapshot.chunks()));
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(vectorsTmp)))) {
            out.writeInt(snapshot.chunks().size());
            for (int i = 0; i < snapshot.chunks().size(); i++) {
                float[] v = snapshot.vectors().vector(i);
                out.writeInt(v == null ? 0 : v.length);
                if (v != null) {
                    for (float x : v) {
                        out.writeFloat(x);
                    }
                }
            }
        }
        move(vectorsTmp, dir.resolve(VECTORS));
        move(chunksTmp, dir.resolve(CHUNKS)); // written last: its presence marks a complete index
    }

    /** @return the stored snapshot, or null if there is none or it cannot be read */
    public Snapshot load(Path dir) {
        Path chunksFile = dir.resolve(CHUNKS);
        Path vectorsFile = dir.resolve(VECTORS);
        if (!Files.isRegularFile(chunksFile) || !Files.isRegularFile(vectorsFile)) {
            return null;
        }
        try {
            Meta meta = mapper.readValue(chunksFile.toFile(), Meta.class);
            if (meta.format() != FORMAT) {
                log.warn("Ignoring the stored index: unknown format {}", meta.format());
                return null;
            }
            float[][] vectors;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(vectorsFile)))) {
                int count = in.readInt();
                if (count != meta.chunks().size()) {
                    log.warn("Ignoring the stored index: {} vectors for {} passages", count, meta.chunks().size());
                    return null;
                }
                vectors = new float[count][];
                for (int i = 0; i < count; i++) {
                    int length = in.readInt();
                    if (length < 0 || length > 65_536) {
                        log.warn("Ignoring the stored index: implausible vector length {}", length);
                        return null;
                    }
                    if (length > 0) {
                        float[] v = new float[length];
                        for (int j = 0; j < length; j++) {
                            v[j] = in.readFloat();
                        }
                        vectors[i] = v;
                    }
                }
            }
            return new Snapshot(meta.chunks(), vectors, meta.manifest(), meta.embedModel());
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring the stored index (it will be rebuilt): {}", e.toString());
            return null;
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
