package clinic.backend.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import clinic.backend.config.properties.ArchiveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * UC-58 BR-58-06 storage: each archive package is written once to {@code <root>/<year>/<soluutru>.json} and made
 * read-only, so it stays intact from creation (Luật GDĐT 2023 Điều 10; Luật Lưu trữ 2024 Điều 33 khoản 2 điểm b).
 * An existing package is never replaced.
 */
@Component
@RequiredArgsConstructor
public class ArchiveStore {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9-]{1,190}");

    private final ArchiveProperties properties;

    /**
     * Writes the package and returns its path relative to the root and its SHA-256 (lowercase hex).
     *
     * <p>The caller has checked, under the visit's row lock, that no archive row uses {@code soluutru}. A file
     * already at the target is therefore an orphan of a closing whose transaction rolled back after the write
     * (BR-58-08); it is kept aside under a {@code .orphan-<millis>} name, never deleted or overwritten.
     */
    public StoredPackage store(int year, String soluutru, byte[] content) {
        if (!SAFE_NAME.matcher(soluutru).matches()) {
            throw new IllegalArgumentException("Unsafe archive number: " + soluutru);
        }
        String relative = year + "/" + soluutru + ".json";
        Path directory = properties.root().resolve(Integer.toString(year));
        Path target = directory.resolve(soluutru + ".json");
        try {
            Files.createDirectories(directory);
            if (Files.exists(target)) {
                Path orphan = directory.resolve(soluutru + ".json.orphan-" + System.currentTimeMillis());
                Files.move(target, orphan);
            }
            Path temporary = Files.createTempFile(directory, soluutru + "-", ".tmp");
            try {
                Files.write(temporary, content);
                // Without REPLACE_EXISTING the move fails when the target already exists.
                Files.move(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            if (!target.toFile().setReadOnly()) {
                throw new IOException("Cannot make the archive package read-only: " + target);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new StoredPackage(relative, sha256(content));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record StoredPackage(String uri, String sha256) {
    }
}
