package clinic.backend.service.impl;

import java.io.Reader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.entity.Icd10;
import clinic.backend.entity.Icd10.CatalogEntry;
import clinic.backend.repository.Icd10Repository;
import clinic.backend.service.Icd10ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads a published ICD-10 list in one transaction (docs/database-conventions.md: no catalog rows in migrations). */
@Service
@RequiredArgsConstructor
public class Icd10ImportServiceImpl implements Icd10ImportService {

    private final Icd10Repository icd10Repository;

    @Override
    @Transactional
    public Result importCatalog(Reader csv, LocalDate effectiveFrom, String source) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (source == null || source.isBlank() || source.length() > 100) {
            throw new IllegalArgumentException("source must have 1-100 characters");
        }
        List<CatalogEntry> entries = Icd10CatalogParser.parse(csv);

        Map<String, Icd10> existing = icd10Repository.findAll().stream()
                .collect(Collectors.toMap(Icd10::getMaicd, Function.identity()));
        List<Icd10> created = new ArrayList<>();
        int updated = 0;
        int unchanged = 0;
        for (CatalogEntry entry : entries) {
            Icd10 current = existing.remove(entry.code());
            if (current == null) {
                created.add(Icd10.fromCatalog(entry, effectiveFrom, source.strip()));
            } else if (current.updateFromCatalog(entry, source.strip())) {
                updated++;
            } else {
                unchanged++;
            }
        }
        // Codes no longer in the list stay for the records that use them, but cannot be selected any more.
        int retired = (int) existing.values().stream().filter(code -> code.retire(effectiveFrom)).count();
        icd10Repository.saveAll(created);
        return new Result(entries.size(), created.size(), updated, unchanged, retired);
    }
}
