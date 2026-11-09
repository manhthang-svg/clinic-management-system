package clinic.backend.service.impl;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import clinic.backend.entity.LongTermPrescriptionDisease.ListEntry;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService.InvalidListException;

/**
 * Reads and validates the CSV made from TT 26/2025/TT-BYT Phụ lục VII
 * ({@code stt,ma_tt,ten_benh,ma_icd_goc,ma_icd}).
 *
 * <p>{@code ma_icd_goc} is the code text as printed. {@code ma_icd} is the same codes in a fixed form, items
 * separated by spaces: {@code E11} is the code and its child codes, {@code F70..F79} a range of codes of the same
 * length with their child codes, and {@code -E11.0} excludes a code and its child codes. Both are empty for a row
 * printed without a code.
 */
final class LongTermDiseaseListParser {

    static final List<String> HEADER = List.of("stt", "ma_tt", "ten_benh", "ma_icd_goc", "ma_icd");
    private static final String CODE = "[A-Z]\\d\\d(?:\\.\\d{1,2})?";
    private static final Pattern ITEM = Pattern.compile("(-)?(" + CODE + ")(?:\\.\\.(" + CODE + "))?");
    private static final Pattern MA_TT = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
    private static final int MAX_ORIGINAL_LENGTH = 255;
    private static final int MAX_REPORTED_ERRORS = 50;

    private LongTermDiseaseListParser() {
    }

    /** A validated row with its code selection. */
    record Row(int line, ListEntry entry, List<CodeRange> includes, List<CodeRange> excludes) {

        /** Whether the code is selected: inside an included range and outside every excluded one. */
        boolean selects(String code) {
            return includes.stream().anyMatch(range -> range.contains(code))
                    && excludes.stream().noneMatch(range -> range.contains(code));
        }
    }

    /** Codes from {@code first} to {@code last} (same length), each with its child codes. */
    record CodeRange(String first, String last) {

        boolean contains(String code) {
            if (code.length() < first.length()) {
                return false;
            }
            String head = code.substring(0, first.length());
            return head.compareTo(first) >= 0 && head.compareTo(last) <= 0;
        }

        @Override
        public String toString() {
            return first.equals(last) ? first : first + ".." + last;
        }
    }

    /** Returns the rows in file order, or throws with every problem found. */
    static List<Row> parse(Reader reader) {
        List<List<String>> rows = Icd10CatalogParser.readCsv(reader);
        if (rows.isEmpty() || !rows.get(0).equals(HEADER)) {
            throw new InvalidListException(List.of("line 1: header must be " + String.join(",", HEADER)));
        }
        List<String> errors = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        List<Row> parsed = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1;
            List<String> row = rows.get(i);
            if (row.size() != HEADER.size()) {
                errors.add("line " + line + ": expected " + HEADER.size() + " columns, found " + row.size());
                continue;
            }
            Row result = toRow(line, row, errors);
            if (result != null && !numbers.add(result.entry().stt())) {
                errors.add("line " + line + ": duplicate stt " + result.entry().stt());
            } else if (result != null) {
                parsed.add(result);
            }
        }
        if (parsed.isEmpty() && errors.isEmpty()) {
            errors.add("the file has no rows");
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

    private static Row toRow(int line, List<String> row, List<String> errors) {
        int before = errors.size();
        Integer stt = null;
        try {
            stt = Integer.valueOf(row.get(0).strip());
            if (stt < 1) {
                errors.add("line " + line + ": stt must be at least 1");
            }
        } catch (NumberFormatException ex) {
            errors.add("line " + line + ": invalid stt '" + row.get(0).strip() + "'");
        }
        String maTt = row.get(1).strip();
        if (!MA_TT.matcher(maTt).matches()) {
            errors.add("line " + line + ": invalid ma_tt '" + maTt + "'");
        }
        String name = String.join(" ", row.get(2).strip().split("\\s+"));
        if (name.isEmpty()) {
            errors.add("line " + line + ": ten_benh is empty");
        }
        String original = row.get(3).strip();
        if (original.length() > MAX_ORIGINAL_LENGTH) {
            errors.add("line " + line + ": ma_icd_goc must have at most " + MAX_ORIGINAL_LENGTH + " characters");
        }
        List<CodeRange> includes = new ArrayList<>();
        List<CodeRange> excludes = new ArrayList<>();
        String codes = row.get(4).strip();
        int beforeItems = errors.size();
        if (!codes.isEmpty()) {
            for (String item : codes.split("\\s+")) {
                readItem(line, item, includes, excludes, errors);
            }
        }
        if (original.isEmpty() != codes.isEmpty()) {
            errors.add("line " + line + ": ma_icd_goc and ma_icd must both be empty or both be given");
        } else if (!codes.isEmpty() && includes.isEmpty() && errors.size() == beforeItems) {
            errors.add("line " + line + ": ma_icd has no included code");
        }
        if (errors.size() > before) {
            return null;
        }
        ListEntry entry = new ListEntry(stt, maTt, name, original.isEmpty() ? null : original);
        return new Row(line, entry, List.copyOf(includes), List.copyOf(excludes));
    }

    private static void readItem(
            int line, String item, List<CodeRange> includes, List<CodeRange> excludes, List<String> errors) {
        Matcher matcher = ITEM.matcher(item);
        if (!matcher.matches()) {
            errors.add("line " + line + ": invalid ma_icd item '" + item + "'");
            return;
        }
        String first = matcher.group(2);
        String last = matcher.group(3) == null ? first : matcher.group(3);
        if (first.length() != last.length() || first.charAt(0) != last.charAt(0) || first.compareTo(last) > 0) {
            errors.add("line " + line + ": invalid range '" + item + "'");
            return;
        }
        (matcher.group(1) == null ? includes : excludes).add(new CodeRange(first, last));
    }
}
