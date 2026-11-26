// hang doi KTV hien 1 dong / phieu cua benh nhan, backend tra 1 item / dich vu -> gom lai thanh dong

// dich vu da vao hang doi phong: da check-in, co so thu tu hoac da lam tiep
export function isServiceCheckedIn(service) {
  return Boolean(
    service?.isCheckedIn
      || (service?.queueNumber != null && service.queueNumber > 0)
      || service?.status === 'IN_PROGRESS'
      || service?.status === 'PENDING_RESULT'
      || service?.status === 'COMPLETED',
  );
}

// dich vu bi bo qua (goi khong co mat)
function isSkippedService(service) {
  return Boolean(service.isSkipped) || service.status === 'SKIPPED';
}

// trang thai cua dong, uu tien viec gap nhat: dang lam > cho > bo qua > cho ket qua > xong > huy
// dong da lam het thi khong duoc quay ve cho (neu khong nut Tiep nhan hien lai)
export function groupStatusOf(services) {
  if (services.some((s) => s.status === 'IN_PROGRESS')) return 'IN_PROGRESS';
  if (services.some((s) => s.status === 'WAITING' && !isSkippedService(s))) return 'WAITING';
  if (services.some((s) => (s.status === 'WAITING' || s.status === 'SKIPPED') && isSkippedService(s))) return 'SKIPPED';
  if (services.some((s) => s.status === 'PENDING_RESULT')) return 'PENDING_RESULT';
  if (services.some((s) => s.status === 'COMPLETED')) return 'COMPLETED';
  return 'CANCELLED';
}

// dich vu ma nut thao tac cua dong ap dung
export function serviceForAction(group, canAct) {
  const services = group.roomServices ?? [group];
  const matches = group.status === 'SKIPPED'
    ? (s) => isSkippedService(s)
    : group.status === 'WAITING'
      ? (s) => s.status === 'WAITING' && !isSkippedService(s)
      : (s) => s.status === group.status;
  return canAct
    ? services.find((service) => matches(service) && canAct(service))
    : services.find(matches) ?? services[0];
}
