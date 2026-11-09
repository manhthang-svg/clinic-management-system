package clinic.backend.dto.response;

import java.util.List;

/** Values for the medicine form and filters: the fixed route list and the units in use (I1-D46). */
public record MedicineFormOptionsResponse(List<String> routes, List<String> units) {
}
