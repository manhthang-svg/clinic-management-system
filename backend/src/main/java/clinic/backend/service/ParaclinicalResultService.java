package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.SaveParaclinicalResultRequest;
import clinic.backend.dto.response.ParaclinicalResultResponse;

/**
 * UC-67 Record Paraclinical Result and the print counter of UC-68. Results are entered by hand (SW-04); only the
 * technician who performed the service records its result, and a submitted result is locked (BR-29).
 */
public interface ParaclinicalResultService {

    /** UC-67 step 2: the service with its result form; read-only for anyone but its performer (EF-03). */
    ParaclinicalResultResponse get(UUID technicianAccountId, UUID orderDetailId);

    /** UC-67 AF-01: saves the result without locking it. */
    ParaclinicalResultResponse saveDraft(UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request);

    /** UC-67 steps 6–10: checks, locks the result, completes the service and moves the visit on when all are done. */
    ParaclinicalResultResponse submit(UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request);

    /** UC-68: counts a print of a submitted result; the second and later prints are marked as a copy. */
    ParaclinicalResultResponse recordPrint(UUID technicianAccountId, UUID orderDetailId);

    /** UC-67: attaches one JPG / PNG image (at most 10 MB, 10 per result) before the result is submitted. */
    ParaclinicalResultResponse uploadImage(
            UUID technicianAccountId, UUID orderDetailId, String fileName, String contentType, byte[] content,
            String caption);

    /** UC-67: removes an image of a result that is not submitted yet. */
    ParaclinicalResultResponse deleteImage(UUID technicianAccountId, UUID orderDetailId, UUID imageId);
}
