package clinic.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Room;

/**
 * Work done once a receipt is paid, shared by the cashier desk (UC-70) and PayOS (UC-71): an examination fee gives
 * the visit its queue number; a paraclinical fee sends each service to the least busy paraclinical room (BR-28).
 */
public interface PaymentCompletionService {

    /** Runs in the caller's transaction; {@code actorAccountId} is empty for a PayOS payment. */
    void completePaidReceipt(Payment payment, UUID actorAccountId);

    /** UC-49: releases an order that needs no payment; returns the services that found no room. */
    List<String> releaseOrder(ParaclinicalOrder order);

    /** The active room of the group with the fewest waiting services; ties go to the lower room code. */
    Optional<Room> leastBusyRoom(UUID serviceGroupId);
}
