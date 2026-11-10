package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.ParaclinicalOrderPrintResponse;

/** UC-50 Print Paraclinical Order. */
public interface ParaclinicalOrderPrintService {

    /**
     * Builds the order slip and records the print (BR-50-08). Only the Doctor who created the order or the Doctor of
     * its visit may print it.
     */
    ParaclinicalOrderPrintResponse preparePrint(UUID doctorAccountId, UUID orderId);
}
