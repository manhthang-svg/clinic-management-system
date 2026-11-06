package clinic.backend.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import clinic.backend.entity.Icd10;
import clinic.backend.entity.Icd10.CatalogEntry;
import clinic.backend.service.Icd10ImportService.InvalidCatalogException;

/**
 * Reads and validates the ICD-10 CSV produced from the TT 06/2026/TT-BYT appendix ({@code ma,ten,ma_cha,ghi_chu};
 * {@code ghi_chu} holds the column 18 and 24-29 rules as {@code |}-separated flags, plus any free-text note).
 */
final class Icd10CatalogParser {

    static final List<String> HEADER = List.of("ma", "ten", "ma_cha", "ghi_chu");
    private static final Pattern CODE = Pattern.compile("[A-Z]\\d\\d(\\.\\d{1,2})?");
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_REPORTED_ERRORS = 50;
    private static final short MALE = 1;
    private static final short FEMALE = 2;

    private Icd10CatalogParser() {
    }

    /** Returns the entries in file order, or throws with every problem found. */
    static List<CatalogEntry> parse(Reader reader) {
        List<List<String>> rows = readCsv(reader);
        List<String> errors = new ArrayList<>();
        if (rows.isEmpty() || !rows.get(0).equals(HEADER)) {
            throw new InvalidCatalogException(List.of("line 1: header must be " + String.join(",", HEADER)));
        }

        Map<String, Integer> lineOf = new HashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == HEADER.size() && lineOf.putIfAbsent(row.get(0).strip(), i + 1) != null) {
                errors.add("line " + (i + 1) + ": duplicate code " + row.get(0).strip());
            }
        }
        Set<String> codes = new HashSet<>(lineOf.keySet());

        List<CatalogEntry> entries = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1;
            List<String> row = rows.get(i);
            if (row.size() != HEADER.size()) {
                errors.add("line " + line + ": expected 4 columns, found " + row.size());
                continue;
            }
            CatalogEntry entry = toEntry(line, row, codes, errors);
            if (entry != null) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty() && errors.isEmpty()) {
            errors.add("the file has no codes");
        }
        if (!errors.isEmpty()) {
            throw new InvalidCatalogException(errors.size() > MAX_REPORTED_ERRORS
                    ? append(errors.subList(0, MAX_REPORTED_ERRORS),
                    "... and " + (errors.size() - MAX_REPORTED_ERRORS) + " more")
                    : errors);
        }
        return entries;
    }

    private static CatalogEntry toEntry(int line, List<String> row, Set<String> codes, List<String> errors) {
        String code = row.get(0).strip();
        String name = String.join(" ", row.get(1).strip().split("\\s+"));
        String givenParent = row.get(2).strip();
        int before = errors.size();

        if (!CODE.matcher(code).matches()) {
            errors.add("line " + line + ": invalid code '" + code + "'");
            return null;
        }
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            errors.add("line " + line + ": name of " + code + " must have 1-" + MAX_NAME_LENGTH + " characters");
        }
        String parent = parentOf(code, codes);
        String category = code.substring(0, 3);
        if (code.length() == 3 ? !givenParent.isEmpty() : !givenParent.equals(category)) {
            errors.add("line " + line + ": ma_cha of " + code + " must be "
                    + (code.length() == 3 ? "empty" : category) + ", found '" + givenParent + "'");
        }
        if (parent != null && !codes.contains(parent)) {
            errors.add("line " + line + ": parent " + parent + " of " + code + " is not in the file");
        }

        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (String token : row.get(3).split("\\|")) {
            String value = token.strip();
            if (value.isEmpty()) continue;
            if (KNOWN_FLAGS.contains(value)) flags.put(value, true);
            else notes.add(value);
        }
        boolean female = flags.containsKey("c28_nu");
        boolean male = flags.containsKey("c29_nam");
        boolean dagger = flags.containsKey("dao_gam");
        boolean asterisk = flags.containsKey("dau_sao");
        if (female && male) {
            errors.add("line " + line + ": " + code + " cannot be for female only and male only");
        }
        if (dagger && asterisk) {
            errors.add("line " + line + ": " + code + " cannot carry both the dagger and the asterisk");
        }
        if (errors.size() > before) {
            return null;
        }
        return new CatalogEntry(code, name, parent,
                flags.containsKey("c24_khong_dung_benh_chinh"),
                flags.containsKey("c25_khong_khuyen_khich_benh_chinh"),
                flags.containsKey("c26_co_ma_chi_tiet_hon"),
                flags.containsKey("c27_chi_ma_tu_vong"),
                female ? Short.valueOf(FEMALE) : male ? Short.valueOf(MALE) : null,
                dagger ? Icd10.DAGGER : asterisk ? Icd10.ASTERISK : null,
                notes.isEmpty() ? null : String.join("; ", notes));
    }

    /** B18.00 -> B18.0 when that code exists, otherwise B18; B18.0 -> B18; B18 -> none. */
    static String parentOf(String code, Set<String> codes) {
        if (code.length() == 3) return null;
        if (code.length() == 6) {
            String subcategory = code.substring(0, 5);
            if (codes.contains(subcategory)) return subcategory;
        }
        return code.substring(0, 3);
    }

    private static final Set<String> KNOWN_FLAGS = Set.of(
            "c24_khong_dung_benh_chinh", "c25_khong_khuyen_khich_benh_chinh", "c26_co_ma_chi_tiet_hon",
            "c27_chi_ma_tu_vong", "c28_nu", "c29_nam", "dao_gam", "dau_sao");

    private static List<String> append(List<String> list, String last) {
        List<String> copy = new ArrayList<>(list);
        copy.add(last);
        return copy;
    }

    /** Minimal RFC 4180 reader: quoted fields, doubled quotes, CRLF or LF, and a leading BOM. */
    static List<List<String>> readCsv(Reader source) {
        try (BufferedReader reader = new BufferedReader(source)) {
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean first = true;
            int c;
            while ((c = reader.read()) != -1) {
                if (first) {
                    first = false;
                    if (c == '﻿') continue;
                }
                if (quoted) {
                    if (c == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) reader.reset();
                        }
                    } else {
                        field.append((char) c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n' || c == '\r') {
                    if (c == '\r') {
                        reader.mark(1);
                        if (reader.read() != '\n') reader.reset();
                    }
                    row.add(field.toString());
                    field.setLength(0);
                    if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                    row = new ArrayList<>();
                } else {
                    field.append((char) c);
                }
            }
            if (field.length() > 0 || !row.isEmpty()) {
                row.add(field.toString());
                rows.add(row);
            }
            return rows;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
