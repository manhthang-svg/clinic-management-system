package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import clinic.backend.config.properties.ArchiveProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveStoreTest {

    @TempDir Path root;

    @Test
    void writesThePackageOnceReadOnlyAndReturnsItsHash() throws Exception {
        byte[] content = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        var stored = new ArchiveStore(new ArchiveProperties(root)).store(2026, "LT-DK0000000123", content);

        Path file = root.resolve("2026/LT-DK0000000123.json");
        assertThat(stored.uri()).isEqualTo("2026/LT-DK0000000123.json");
        assertThat(Files.readAllBytes(file)).isEqualTo(content);
        assertThat(Files.isWritable(file)).isFalse();
        // SHA-256 of {"a":1}.
        assertThat(stored.sha256())
                .isEqualTo("015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862")
                .matches("[0-9a-f]{64}");
        try (var files = Files.list(root.resolve("2026"))) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void keepsAnOrphanOfARolledBackClosingAsideWithoutOverwritingIt() throws Exception {
        ArchiveStore store = new ArchiveStore(new ArchiveProperties(root));
        store.store(2026, "LT-DK0001", "old".getBytes(StandardCharsets.UTF_8));

        store.store(2026, "LT-DK0001", "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(root.resolve("2026/LT-DK0001.json"))).isEqualTo("new");
        try (var files = Files.list(root.resolve("2026"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("LT-DK0001.json.orphan-"))
                    .hasSize(2);
        }
    }

    @Test
    void refusesAnArchiveNumberThatIsNotASafeFileName() {
        ArchiveStore store = new ArchiveStore(new ArchiveProperties(root));

        assertThatThrownBy(() -> store.store(2026, "../LT-1", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
