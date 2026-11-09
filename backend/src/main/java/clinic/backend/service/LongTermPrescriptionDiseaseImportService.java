package clinic.backend.service;

import java.io.Reader;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads the list of diseases whose medicines may be prescribed for more than 30 days (TT 26/2025/TT-BYT Phụ lục
 * VII) and expands each row's ICD-10 codes against the icd10 catalog, which must be loaded first. Run only by the
 * long-term-disease-import operator profile. The whole file is validated before anything is written; one run is
 * one transaction.
 */
public interface LongTermPrescriptionDiseaseImportService {

    /**
     * Adds new rows, updates changed ones and retires rows of the same document no longer in the list. Running the
     * same file again changes nothing.
     *
     * @param csv           UTF-8 CSV with the header {@code stt,ma_tt,ten_benh,ma_icd_goc,ma_icd}
     * @param effectiveFrom the day the list takes effect; retired rows end the day before
     * @param source        the document the list comes from, for example {@code TT 26/2025/TT-BYT}
     * @throws InvalidListException when any row is invalid; nothing is written
     */
    Result importList(Reader csv, LocalDate effectiveFrom, String source);

    /**
     * @param codes    ICD-10 codes linked to the rows after the import
     * @param warnings codes of the list that are not in the icd10 catalog; they are skipped
     */
    record Result(int total, int created, int updated, int unchanged, int retired, int codes, List<String> warnings) {

        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    /** The file was rejected; {@link #errors()} lists the problems with their line numbers. */
    final class InvalidListException extends RuntimeException {

        private final List<String> errors;

        public InvalidListException(List<String> errors) {
            super("Invalid long-term prescription disease list: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
