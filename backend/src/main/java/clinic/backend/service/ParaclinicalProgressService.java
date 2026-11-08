package clinic.backend.service;

import clinic.backend.entity.Visit;

/**
 * UC-67, BR-41 (docs/state-transitions.md V14, S5): once every paraclinical service of a visit has finished, the
 * visit moves from Waiting for Paraclinical to Waiting for Conclusion. Call it in the same transaction whenever a
 * service finishes: completed by the Technician (UC-67) or cancelled by the Doctor (BR-25).
 */
public interface ParaclinicalProgressService {

    /** Returns true when the visit was moved to Waiting for Conclusion. */
    boolean moveToConclusionIfDone(Visit visit);
}
