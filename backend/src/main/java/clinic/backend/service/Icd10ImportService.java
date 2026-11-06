package clinic.backend.service;

import java.io.Reader;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads a published ICD-10 list (TT 06/2026/TT-BYT) into the icd10 catalog. Run only by the icd10-import
 * operator profile. The whole file is validated before anything is written; one run is one transaction.
 */
public interface Icd10ImportService {

    /**
     * Adds new codes, updates changed ones and retires codes no longer in the list. Running the same file again
     * changes nothing.
     *
     * @param csv           UTF-8 CSV with the header {@code ma,ten,ma_cha,ghi_chu}
     * @param effectiveFrom the day the list takes effect; new codes start then and retired codes end the day before
     * @param source        the document the list comes from, for example {@code TT 06/2026/TT-BYT}
     * @throws InvalidCatalogException when any row is invalid; nothing is written
     */
    Result importCatalog(Reader csv, LocalDate effectiveFrom, String source);

    record Result(int total, int created, int updated, int unchanged, int retired) {
    }

    /** The file was rejected; {@link #errors()} lists the problems with their line numbers. */
    final class InvalidCatalogException extends RuntimeException {

        private final List<String> errors;

        public InvalidCatalogException(List<String> errors) {
            super("Invalid ICD-10 catalog: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
