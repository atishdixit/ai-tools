package com.example.chatbot.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexStorageTest {

    @TempDir
    Path dir;

    private final IndexStorage storage = new IndexStorage();

    private static Snapshot sample() {
        return new Snapshot(
                List.of(new Chunk("a.md#1", "a.md", "A", "A\nfirst café ₹"), new Chunk("b.md#1", "b.md", "B", "B\nsecond")),
                new float[][] {{0.1f, 0.2f, 0.3f}, null},
                Map.of("a.md", "hash-a", "b.md", "hash-b"),
                "nomic-embed-text");
    }

    @Test
    void aSnapshotSurvivesASaveAndLoad() throws IOException {
        storage.save(dir, sample());
        Snapshot loaded = storage.load(dir);
        assertThat(loaded).isNotNull();
        assertThat(loaded.chunks()).containsExactlyElementsOf(sample().chunks());
        assertThat(loaded.manifest()).containsEntry("a.md", "hash-a").containsEntry("b.md", "hash-b");
        assertThat(loaded.embedModel()).isEqualTo("nomic-embed-text");
        assertThat(loaded.vectors().vector(1)).isNull();
        assertThat(loaded.vectors().vector(0)).hasSize(3);
        assertThat(loaded.vectorsComplete()).isFalse();
    }

    @Test
    void nothingStoredMeansNull() {
        assertThat(storage.load(dir)).isNull();
        assertThat(storage.load(dir.resolve("does-not-exist"))).isNull();
    }

    @Test
    void aCorruptChunksFileIsIgnored() throws IOException {
        storage.save(dir, sample());
        Files.writeString(dir.resolve("chunks.json"), "{ not json");
        assertThat(storage.load(dir)).isNull();
    }

    @Test
    void aTruncatedVectorFileIsIgnored() throws IOException {
        storage.save(dir, sample());
        byte[] bytes = Files.readAllBytes(dir.resolve("vectors.bin"));
        Files.write(dir.resolve("vectors.bin"), java.util.Arrays.copyOf(bytes, bytes.length - 6));
        assertThat(storage.load(dir)).isNull();
    }

    @Test
    void aVectorFileForADifferentNumberOfPassagesIsIgnored() throws IOException {
        storage.save(dir, sample());
        Snapshot other = new Snapshot(List.of(new Chunk("x", "x.md", "X", "X\ny")), new float[][] {{1f}}, Map.of(), "m");
        Path otherDir = Files.createDirectory(dir.resolve("other"));
        storage.save(otherDir, other);
        Files.copy(otherDir.resolve("vectors.bin"), dir.resolve("vectors.bin"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThat(storage.load(dir)).isNull();
    }

    @Test
    void savingCreatesTheFolderAndLeavesNoTemporaryFiles() throws IOException {
        Path nested = dir.resolve("a/b/index");
        storage.save(nested, sample());
        try (var files = Files.list(nested)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder("chunks.json", "vectors.bin");
        }
    }

    @Test
    void anEmptySnapshotRoundTrips() throws IOException {
        storage.save(dir, Snapshot.empty("m"));
        assertThat(storage.load(dir)).isNotNull().satisfies(s -> assertThat(s.isEmpty()).isTrue());
    }
}
