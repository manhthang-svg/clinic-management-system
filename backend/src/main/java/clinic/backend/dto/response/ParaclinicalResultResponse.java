package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.enums.ResultForm;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * UC-67 step 2: an ordered paraclinical service with its result form, as the result page reads it.
 *
 * @param id           the ordered service (paraclinical order detail)
 * @param kind         LAB, IMAGING or FUNCTIONAL
 * @param status       IN_PROGRESS, PERFORMED (waiting for its result) or COMPLETED
 * @param isPerformer  true when the signed-in technician performed the service and may edit its result (EF-03)
 * @param parameters   lab parameters of the service, empty for descriptive results
 * @param result       the saved draft or submitted result, null before the first save
 * @param instruments  instruments of the room that may record this kind of result (V54)
 * @param registrationCode visit registration code printed on the slip (UC-68 step 2)
 * @param clinic       header of the printed slip (UC-77); null while the clinic facility is not configured
 */
public record ParaclinicalResultResponse(
        UUID id,
        String orderCode,
        ResultForm printForm,
        String kind,
        String section,
        String serviceCode,
        String serviceName,
        String orderingDoctor,
        String orderingRoom,
        String department,
        String performingDepartment,
        String priority,
        Patient patient,
        String diagnosis,
        String clinicalNote,
        String symptoms,
        Screening screening,
        Instant orderedAt,
        Instant performedAt,
        String status,
        Performer performer,
        @JsonProperty("isPerformer") boolean isPerformer,
        List<Parameter> parameters,
        Result result,
        int printCount,
        List<Instrument> instruments,
        String registrationCode,
        Clinic clinic,
        List<SiblingService> siblingServices
) {

    public record SiblingService(
            UUID id,
            String orderCode,
            String serviceName,
            String serviceCode,
            ResultForm printForm,
            String status,
            UUID roomId,
            boolean isCurrentRoom
    ) {}

    /** TBD: the parent agency ("Sở Y tế …") is not a facility field yet, so it is not printed. */
    public record Clinic(String name, String address, String phone) {
    }

    public record Patient(String code, String name, Integer age, String gender, short sex, String address,
                          String coverage, String insuranceNumber, String ethnicity, String phone) {
    }

    public record Screening(String metalImplant, String priorResults) {
    }

    public record Performer(UUID id, String name) {
    }

    /** A lab parameter with its reference range in force; {@code ref} is null when no range applies. */
    public record Parameter(String code, String name, String unit, String valueType, String group, boolean required,
                            Reference ref) {
    }

    /** RANGE (low–high), UPPER (normal below high), LOWER (normal above low), QUALITATIVE or TEXT. */
    public record Reference(String type, BigDecimal low, BigDecimal high, List<String> normal, String text) {
    }

    public record Instrument(UUID id, String code, String name) {
    }

    public record Result(
            String status,
            Instant savedAt,
            Instant submittedAt,
            Instant receivedAt,
            Map<String, String> values,
            Map<String, String> interpretations,
            String conclusion,
            String technique,
            String description,
            Map<String, String> sections,
            String recommendation,
            String imageLink,
            String readingDoctor,
            UUID instrumentId,
            String instrumentName,
            List<Object> files
    ) {
    }
}
