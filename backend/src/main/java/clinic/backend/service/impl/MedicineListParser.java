package clinic.backend.service.impl;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import clinic.backend.entity.MedicineCatalog.CatalogEntry;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.MedicineRoute;
import clinic.backend.service.MedicineCatalogImportService.InvalidListException;

/**
 * Reads and validates a medicine list against BR-79. The header starts with {@link #HEADER}; any further columns
 * (for example the dosage form, packaging and registration decision of the bundled list) record the source and are
 * ignored.
 */
final class MedicineListParser {

    static final List<String> HEADER = List.of("ma_thuoc", "so_dang_ky", "ma_thuoc_bhyt", "ten_thuoc", "hoat_chat",
            "ham_luong", "duong_dung", "don_vi_tinh", "nhom_kiem_soat");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9-]{1,50}");
    private static final int MAX_REPORTED_ERRORS = 50;

    private MedicineListParser() {
    }

    /** A validated row with its line number. */
    record Row(int line, CatalogEntry entry) {
    }

    /** Returns the rows in file order, or throws with every problem found. */
    static List<Row> parse(Reader reader) {
        List<List<String>> rows = Icd10CatalogParser.readCsv(reader);
        if (rows.isEmpty() || rows.get(0).size() < HEADER.size()
                || !rows.get(0).subList(0, HEADER.size()).equals(HEADER)) {
            throw new InvalidListException(List.of("line 1: header must start with " + String.join(",", HEADER)));
        }
        int columns = rows.get(0).size();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> codeLines = new HashMap<>();
        Map<String, Integer> registrationLines = new HashMap<>();
        List<Row> parsed = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1;
            List<String> row = rows.get(i);
            if (row.size() != columns) {
                errors.add("line " + line + ": expected " + columns + " columns, found " + row.size());
                continue;
            }
            CatalogEntry entry = toEntry(line, row, errors);
            if (entry == null) {
                continue;
            }
            Integer sameCode = codeLines.putIfAbsent(entry.code(), line);
            Integer sameRegistration = registrationLines.putIfAbsent(entry.soDangKy(), line);
            if (sameCode != null) {
                errors.add("line " + line + ": ma_thuoc " + entry.code() + " is already on line " + sameCode);
            } else if (sameRegistration != null) {
                errors.add("line " + line + ": so_dang_ky " + entry.soDangKy() + " is already on line "
                        + sameRegistration);
            } else {
                parsed.add(new Row(line, entry));
            }
        }
        if (parsed.isEmpty() && errors.isEmpty()) {
            errors.add("the file has no medicines");
        }
        if (!errors.isEmpty()) {
            List<String> reported = new ArrayList<>(errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
            if (errors.size() > MAX_REPORTED_ERRORS) {
                reported.add("... and " + (errors.size() - MAX_REPORTED_ERRORS) + " more");
            }
            throw new InvalidListException(reported);
        }
        return parsed;
    }

    private static CatalogEntry toEntry(int line, List<String> row, List<String> errors) {
        int before = errors.size();
        String code = row.get(0).strip();
        if (!CODE.matcher(code).matches()) {
            errors.add("line " + line + ": ma_thuoc must have 1-50 upper-case letters, digits or hyphens, found '"
                    + code + "'");
        }
        String registration = text(line, "so_dang_ky", row.get(1), 1, 20, errors);
        String insuranceCode = row.get(2).strip();
        if (insuranceCode.length() > 255) {
            errors.add("line " + line + ": ma_thuoc_bhyt must have at most 255 characters");
        }
        String name = text(line, "ten_thuoc", row.get(3), 2, 255, errors);
        String ingredient = text(line, "hoat_chat", row.get(4), 2, 500, errors);
        String strength = text(line, "ham_luong", row.get(5), 1, Integer.MAX_VALUE, errors);
        String route = text(line, "duong_dung", row.get(6), 1, Integer.MAX_VALUE, errors);
        if (route != null && MedicineRoute.fromLabel(route).isEmpty()) {
            errors.add("line " + line + ": duong_dung '" + route + "' is not in the route list");
        }
        String unit = text(line, "don_vi_tinh", row.get(7), 1, 50, errors);
        MedicineControlGroup group = null;
        try {
            group = MedicineControlGroup.valueOf(row.get(8).strip());
        } catch (IllegalArgumentException ex) {
            errors.add("line " + line + ": nhom_kiem_soat must be one of THUONG, GAY_NGHIEN, HUONG_THAN, TIEN_CHAT,"
                    + " found '" + row.get(8).strip() + "'");
        }
        if (errors.size() > before) {
            return null;
        }
        return new CatalogEntry(code, registration, insuranceCode.isEmpty() ? null : insuranceCode, name, ingredient,
                strength, route, unit, group);
    }

    /** Collapses whitespace and checks the length; returns null after recording an error. */
    private static String text(int line, String column, String raw, int min, int max, List<String> errors) {
        String value = String.join(" ", raw.strip().split("\\s+")).strip();
        if (value.length() < min || value.length() > max) {
            errors.add("line " + line + ": " + column + " must have " + min
                    + (max == Integer.MAX_VALUE ? " or more" : "-" + max) + " characters");
            return null;
        }
        return value;
    }
}
