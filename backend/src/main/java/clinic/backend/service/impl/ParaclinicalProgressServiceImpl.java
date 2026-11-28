package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ParaclinicalProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParaclinicalProgressServiceImpl implements ParaclinicalProgressService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final VisitRepository visitRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean moveToConclusionIfDone(Visit visit) {
        if (visit.getStatus() != VisitStatus.WAITING_PARACLINICAL) {
            return false;
        }
        // Khóa dòng visits (PESSIMISTIC_WRITE) để tuần tự hóa kiểm tra khi nhiều kết quả cuối được gửi đồng thời
        Visit lockedVisit = visitRepository.findByIdForUpdate(visit.getId()).orElse(visit);
        if (lockedVisit.getStatus() != VisitStatus.WAITING_PARACLINICAL) {
            return false;
        }
        List<ParaclinicalOrderDetail> services = medicalRecordRepository.findByVisitId(lockedVisit.getId())
                .map(record -> paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId()))
                .orElse(List.of())
                .stream()
                .flatMap(order -> order.getDetails().stream())
                .toList();
        // S5: every service finished, and none abandoned (an abandoned service means the visit is being closed).
        boolean done = services.stream().allMatch(ParaclinicalOrderDetail::isFinished)
                && services.stream().noneMatch(detail -> detail.getStatus() == ParaclinicalOrderDetailStatus.ABANDONED);
        if (!done) {
            return false;
        }

        Instant now = clock.instant();
        lockedVisit.markAsWaitingConclusion(now);
        visitRepository.save(lockedVisit);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fromStatus", VisitStatus.WAITING_PARACLINICAL.name());
        values.put("status", lockedVisit.getStatus().name());
        values.put("rule", "S5");
        auditLogService.recordSystemChange(
                AuditLogAction.UPDATE, "visits", lockedVisit.getId(), lockedVisit.getSovaovien(), values, now);
        return true;
    }
}
