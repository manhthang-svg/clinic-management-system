package clinic.backend.utils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A fixed name-to-code list; names match ignoring case and extra spaces. */
final class CodeCatalog {

    private final Map<String, String> codesByName = new LinkedHashMap<>();

    /** {@code entries} are {code, name} pairs. */
    CodeCatalog(String[][] entries) {
        for (String[] entry : entries) {
            alias(entry[1], entry[0]);
        }
    }

    /** Also accept an older spelling of a listed name. */
    CodeCatalog alias(String name, String code) {
        codesByName.put(key(name), code);
        return this;
    }

    Optional<String> codeOf(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(codesByName.get(key(name)));
    }

    private static String key(String name) {
        return String.join(" ", name.strip().split("\\s+")).toLowerCase(Locale.ROOT);
    }
}
