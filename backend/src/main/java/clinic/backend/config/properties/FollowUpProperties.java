package clinic.backend.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * BR-124: a follow-up appointment can be linked to a visit from its date up to {@code validityWorkingDays} working
 * days after it (Saturday and Sunday are not counted; public holidays are not excluded automatically).
 */
@Validated
@ConfigurationProperties(prefix = "clinic.follow-up")
public record FollowUpProperties(@DefaultValue("10") @Min(0) int validityWorkingDays) {
}
