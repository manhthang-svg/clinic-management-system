package clinic.backend.config;

import java.util.List;

import clinic.backend.config.FlywayChecksumRealignmentConfig.ChecksumCorrection;
import clinic.backend.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A database that applied V57 before it was edited starts again without a manual Flyway repair. */
class FlywayChecksumRealignmentConfigIT extends PostgresIntegrationTest {

    private static final ChecksumCorrection V57 = FlywayChecksumRealignmentConfig.CORRECTIONS.getFirst();

    @Autowired
    private Flyway flyway;

    @Autowired
    private FlywayMigrationStrategy strategy;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restoreCurrentChecksum() {
        setV57Checksum(V57.newChecksum());
    }

    @Test
    void theCorrectionMatchesTheCurrentV57File() {
        assertThat(v57Checksum()).isEqualTo(V57.newChecksum());
    }

    @Test
    void realignsTheOldV57ChecksumBeforeMigrating() {
        setV57Checksum(V57.oldChecksum());
        assertThatThrownBy(flyway::validate).isInstanceOf(FlywayValidateException.class);

        strategy.migrate(flyway);

        assertThat(v57Checksum()).isEqualTo(V57.newChecksum());
        assertThatCode(flyway::validate).doesNotThrowAnyException();
    }

    @Test
    void runningAgainChangesNothing() {
        strategy.migrate(flyway);
        strategy.migrate(flyway);

        assertThat(v57Checksum()).isEqualTo(V57.newChecksum());
    }

    @Test
    void leavesAnUnexpectedChecksumForFlywayToReport() {
        setV57Checksum(12345);

        FlywayChecksumRealignmentConfig.realignChecksums(flyway, List.of(V57));

        assertThat(v57Checksum()).isEqualTo(12345);
        assertThatThrownBy(flyway::validate).isInstanceOf(FlywayValidateException.class);
    }

    private Integer v57Checksum() {
        return jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '57'", Integer.class);
    }

    private void setV57Checksum(int checksum) {
        jdbc.update("UPDATE flyway_schema_history SET checksum = ? WHERE version = '57'", checksum);
    }
}
