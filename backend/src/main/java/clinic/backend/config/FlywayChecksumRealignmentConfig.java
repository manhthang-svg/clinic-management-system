package clinic.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Realigns the stored checksum of migrations that were edited after some databases had applied them, then migrates.
 *
 * <p>V57 gained {@code DROP CONSTRAINT IF EXISTS ck_visits_queue_number_issued} (PR #194) after many databases had
 * applied the original file. On those databases the constraint already exists, so the new statement changes
 * nothing, but Flyway rejects the changed checksum. Each correction updates one history row, only while it still
 * holds the old checksum, so it is safe to run on every start and does nothing on a fresh database.
 *
 * <p>TBD: remove a correction once every team database has started with it at least once.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayChecksumRealignmentConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayChecksumRealignmentConfig.class);

    /** A migration whose file changed after it was applied; {@code oldChecksum} is what the history still holds. */
    record ChecksumCorrection(String version, int oldChecksum, int newChecksum) {
    }

    static final List<ChecksumCorrection> CORRECTIONS = List.of(
            new ChecksumCorrection("57", 604395903, -1266406265));

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            realignChecksums(flyway, CORRECTIONS);
            flyway.migrate();
        };
    }

    static void realignChecksums(Flyway flyway, List<ChecksumCorrection> corrections) {
        String table = historyTable(flyway);
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, table)) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET checksum = ? WHERE version = ? AND checksum = ?")) {
                for (ChecksumCorrection correction : corrections) {
                    update.setInt(1, correction.newChecksum());
                    update.setString(2, correction.version());
                    update.setInt(3, correction.oldChecksum());
                    if (update.executeUpdate() > 0) {
                        log.info("Realigned Flyway checksum of migration V{}", correction.version());
                    }
                }
            }
        } catch (SQLException exception) {
            throw new FlywayException("Could not realign Flyway checksums", exception);
        }
    }

    private static String historyTable(Flyway flyway) {
        String table = quote(flyway.getConfiguration().getTable());
        String schema = flyway.getConfiguration().getDefaultSchema();
        return schema == null || schema.isBlank() ? table : quote(schema) + "." + table;
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            query.setString(1, table);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
