package clinic.backend.service.impl;

import java.io.Reader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.entity.LongTermPrescriptionDisease;
import clinic.backend.entity.LongTermPrescriptionDiseaseCode;
import clinic.backend.repository.Icd10Repository;
import clinic.backend.repository.LongTermPrescriptionDiseaseCodeRepository;
import clinic.backend.repository.LongTermPrescriptionDiseaseRepository;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService;
import clinic.backend.service.impl.LongTermDiseaseListParser.CodeRange;
import clinic.backend.service.impl.LongTermDiseaseListParser.Row;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads Phụ lục VII in one transaction (docs/database-conventions.md: no catalog rows in migrations). */
@Service
@RequiredArgsConstructor
public class LongTermPrescriptionDiseaseImportServiceImpl implements LongTermPrescriptionDiseaseImportService {

    private final LongTermPrescriptionDiseaseRepository diseaseRepository;
    private final LongTermPrescriptionDiseaseCodeRepository codeRepository;
    private final Icd10Repository icd10Repository;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public Result importList(Reader csv, LocalDate effectiveFrom, String source) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (source == null || source.isBlank() || source.length() > 100) {
            throw new IllegalArgumentException("source must have 1-100 characters");
        }
        String vanBan = source.strip();
        List<Row> rows = LongTermDiseaseListParser.parse(csv);
        Set<String> catalog = new TreeSet<>(icd10Repository.findAllCodes());
        if (catalog.isEmpty()) {
            throw new IllegalStateException("The icd10 catalog is empty; run the icd10-import profile first");
        }
        Instant now = clock.instant();

        Map<Integer, LongTermPrescriptionDisease> existing = diseaseRepository.findAllByVanBan(vanBan).stream()
                .collect(Collectors.toMap(LongTermPrescriptionDisease::getStt, Function.identity()));
        Map<UUID, Set<String>> linked = new HashMap<>();
        if (!existing.isEmpty()) {
            codeRepository.findAllByDiseaseIdIn(existing.values().stream().map(LongTermPrescriptionDisease::getId)
                            .toList())
                    .forEach(code -> linked.computeIfAbsent(code.getDiseaseId(), id -> new TreeSet<>())
                            .add(code.getMaicd()));
        }

        List<LongTermPrescriptionDisease> created = new ArrayList<>();
        List<LongTermPrescriptionDiseaseCode> addedCodes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int updated = 0;
        int unchanged = 0;
        int codes = 0;
        for (Row row : rows) {
            Set<String> selected = select(row, catalog, warnings);
            codes += selected.size();
            LongTermPrescriptionDisease disease = existing.remove(row.entry().stt());
            Set<String> current = Set.of();
            if (disease == null) {
                disease = new LongTermPrescriptionDisease(ids.next(), now, row.entry(), vanBan, effectiveFrom);
                created.add(disease);
            } else {
                current = linked.getOrDefault(disease.getId(), Set.of());
                boolean rowChanged = disease.updateFromList(row.entry(), now);
                if (rowChanged || !current.equals(selected)) {
                    updated++;
                } else {
                    unchanged++;
                }
            }
            UUID diseaseId = disease.getId();
            for (String code : selected) {
                if (!current.contains(code)) {
                    addedCodes.add(new LongTermPrescriptionDiseaseCode(ids.next(), now, diseaseId, code));
                }
            }
            Set<String> removed = new TreeSet<>(current);
            removed.removeAll(selected);
            if (!removed.isEmpty()) {
                codeRepository.deleteAll(codeRepository.findAllByDiseaseIdIn(List.of(diseaseId)).stream()
                        .filter(code -> removed.contains(code.getMaicd())).toList());
            }
        }
        // Rows no longer in the list stay for history but stop allowing long prescriptions.
        int retired = (int) existing.values().stream().filter(disease -> disease.retire(effectiveFrom, now)).count();
        diseaseRepository.saveAll(created);
        diseaseRepository.flush();
        codeRepository.saveAll(addedCodes);
        return new Result(rows.size(), created.size(), updated, unchanged, retired, codes, warnings);
    }

    /** The catalog codes a row selects; an included item that matches no code is reported and skipped. */
    private static Set<String> select(Row row, Set<String> catalog, List<String> warnings) {
        for (CodeRange range : row.includes()) {
            if (catalog.stream().noneMatch(range::contains)) {
                warnings.add("line " + row.line() + " (stt " + row.entry().stt() + "): " + range
                        + " is not in the ICD-10 catalog and was skipped");
            }
        }
        return catalog.stream().filter(row::selects).collect(Collectors.toCollection(TreeSet::new));
    }
}
