package clinic.backend.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text folding for accent- and case-insensitive Vietnamese search. */
public final class VietnameseText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private VietnameseText() {
    }

    /** "  Viêm phổi  ĐƯỜNG " -> "viem phoi duong"; null stays null. */
    public static String fold(String text) {
        if (text == null) return null;
        String withoutMarks = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        // NFD does not split đ/Đ into d plus a mark.
        String folded = withoutMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return SPACES.matcher(folded).replaceAll(" ").strip();
    }
}
