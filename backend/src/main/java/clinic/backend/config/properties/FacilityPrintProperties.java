package clinic.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Values printed on BYT forms that the clinic facility (UC-77) does not hold yet.
 *
 * @param healthDepartment "Sở Y tế" in the header of form 15/BV1 (UC-53, decision 1a; **ASSUMPTION** until UC-77
 *                         declares it); blank prints an empty field
 */
@ConfigurationProperties(prefix = "clinic.facility")
public record FacilityPrintProperties(String healthDepartment) {
}
