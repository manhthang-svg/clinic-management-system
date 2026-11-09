package clinic.backend.service.impl;

import java.io.Reader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.entity.MedicineCatalog;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.service.MedicineCatalogImportService;
import clinic.backend.service.impl.MedicineListParser.Row;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads a medicine list in one transaction (docs/database-conventions.md: no catalog rows in migrations). */
@Service
@RequiredArgsConstructor
public class MedicineCatalogImportServiceImpl implements MedicineCatalogImportService {

    private final MedicineCatalogRepository medicineRepository;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public Result importList(Reader csv) {
        List<Row> rows = MedicineListParser.parse(csv);
        List<MedicineCatalog> all = medicineRepository.findAll();
        Map<String, MedicineCatalog> byCode = all.stream()
                .collect(Collectors.toMap(MedicineCatalog::getMathuocvattuBv, Function.identity()));
        Map<String, MedicineCatalog> byRegistration = all.stream()
                .collect(Collectors.toMap(MedicineCatalog::getSoDangKy, Function.identity()));

        List<String> conflicts = new ArrayList<>();
        for (Row row : rows) {
            MedicineCatalog owner = byRegistration.get(row.entry().soDangKy());
            if (owner != null && !owner.getMathuocvattuBv().equals(row.entry().code())) {
                conflicts.add("line " + row.line() + ": so_dang_ky " + row.entry().soDangKy()
                        + " belongs to medicine " + owner.getMathuocvattuBv() + " in the catalog");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new InvalidListException(conflicts);
        }

        Instant now = clock.instant();
        List<MedicineCatalog> created = new ArrayList<>();
        int updated = 0;
        int unchanged = 0;
        for (Row row : rows) {
            MedicineCatalog current = byCode.get(row.entry().code());
            if (current == null) {
                created.add(new MedicineCatalog(ids.next(), now, row.entry()));
            } else if (current.updateFromList(row.entry(), now)) {
                updated++;
            } else {
                unchanged++;
            }
        }
        medicineRepository.saveAll(created);
        return new Result(rows.size(), created.size(), updated, unchanged);
    }
}
