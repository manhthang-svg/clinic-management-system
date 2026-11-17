package clinic.backend.config.properties;

import java.nio.file.Path;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * UC-58 BR-58-06: the directory that holds the write-once archive packages of closed medical records. Backups of
 * it, including an off-site copy (CV 365 mục III.2), are an operations task.
 */
@Validated
@ConfigurationProperties(prefix = "clinic.archive")
public record ArchiveProperties(@NotNull Path root) {
}
