package clinic.backend.service;

import java.io.Reader;
import java.util.List;

/**
 * Loads a list of medicines into the medicine catalog (BR-79). Run only by the medicine-import operator profile.
 * The whole file is validated before anything is written; one run is one transaction.
 */
public interface MedicineCatalogImportService {

    /**
     * Adds new medicines and updates changed ones, matched by the internal code. Medicines not in the file are left
     * as they are: activating and deactivating stays with the Admin (UC-84). Running the same file again changes
     * nothing.
     *
     * @param csv UTF-8 CSV whose header starts with
     *            {@code ma_thuoc,so_dang_ky,ma_thuoc_bhyt,ten_thuoc,hoat_chat,ham_luong,duong_dung,don_vi_tinh,
     *            nhom_kiem_soat}; further columns record the source and are not stored
     * @throws InvalidListException when any row is invalid, or a registration number belongs to another
     *                              medicine already in the catalog; nothing is written
     */
    Result importList(Reader csv);

    record Result(int total, int created, int updated, int unchanged) {
    }

    /** The file was rejected; {@link #errors()} lists the problems with their line numbers. */
    final class InvalidListException extends RuntimeException {

        private final List<String> errors;

        public InvalidListException(List<String> errors) {
            super("Invalid medicine list: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
