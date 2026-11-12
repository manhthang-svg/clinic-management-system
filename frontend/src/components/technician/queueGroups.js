/**
 * The technician queue shows one row per patient order in a room, while the backend returns one item per service.
 * These helpers derive the row from its services.
 */

/** A service is in the room queue once the patient checked in, has a number, or the service went further. */
export function isServiceCheckedIn(service) {
  return Boolean(
    service?.isCheckedIn
      || (service?.queueNumber != null && service.queueNumber > 0)
      || service?.status === 'IN_PROGRESS'
      || service?.status === 'PENDING_RESULT'
      || service?.status === 'COMPLETED',
  );
}

function isSkippedService(service) {
  return Boolean(service.isSkipped) || service.status === 'SKIPPED';
}

/**
 * Status of a patient row, from the most to the least urgent work left:
 * IN_PROGRESS → WAITING → SKIPPED → PENDING_RESULT (performed, result not entered) → COMPLETED → CANCELLED.
 * A row whose services were all performed must never fall back to WAITING, or "Tiếp nhận" shows again.
 */
export function groupStatusOf(services) {
  if (services.some((s) => s.status === 'IN_PROGRESS')) return 'IN_PROGRESS';
  if (services.some((s) => s.status === 'WAITING' && !isSkippedService(s))) return 'WAITING';
  if (services.some((s) => (s.status === 'WAITING' || s.status === 'SKIPPED') && isSkippedService(s))) return 'SKIPPED';
  if (services.some((s) => s.status === 'PENDING_RESULT')) return 'PENDING_RESULT';
  if (services.some((s) => s.status === 'COMPLETED')) return 'COMPLETED';
  return 'CANCELLED';
}

/** The service a row action applies to: the first one in the row's status, otherwise the first service. */
export function serviceForAction(group) {
  const services = group.roomServices ?? [group];
  const matches = group.status === 'SKIPPED'
    ? (s) => isSkippedService(s)
    : group.status === 'WAITING'
      ? (s) => s.status === 'WAITING' && !isSkippedService(s)
      : (s) => s.status === group.status;
  return services.find(matches) ?? services[0];
}
