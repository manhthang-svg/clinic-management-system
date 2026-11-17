package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;

/** UC-61 BR-61-02 (BR-108): the follow-up date and advice, without any state. */
final class FollowUpRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_DAYS_AHEAD = 365;
    static final int ADVICE_MAX = 2000;

    private FollowUpRules() {
    }

    /** The visit date in the clinic's time zone. */
    static LocalDate visitDate(Instant registeredAt) {
        return LocalDate.ofInstant(registeredAt, CLINIC_ZONE);
    }

    static LocalDate minDate(LocalDate visitDate) {
        return visitDate.plusDays(1);
    }

    static LocalDate maxDate(LocalDate visitDate) {
        return visitDate.plusDays(MAX_DAYS_AHEAD);
    }

    /** The follow-up date: required, after the visit date and at most 365 days after it. */
    static LocalDate requireDate(LocalDate followUpDate, LocalDate visitDate) {
        if (followUpDate == null) {
            throw invalid("followUpDate", "REQUIRED");
        }
        if (!followUpDate.isAfter(visitDate)) {
            throw invalid("followUpDate", "NOT_AFTER_VISIT");
        }
        if (followUpDate.isAfter(maxDate(visitDate))) {
            throw invalid("followUpDate", "TOO_FAR");
        }
        return followUpDate;
    }

    /** The advice trimmed, null when empty, at most 2,000 characters. */
    static String advice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > ADVICE_MAX) {
            throw invalid("advice", "TOO_LONG");
        }
        return text;
    }

    private static AppException invalid(String field, String reason) {
        return new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_INVALID, Map.of("field", field, "reason", reason));
    }
}
