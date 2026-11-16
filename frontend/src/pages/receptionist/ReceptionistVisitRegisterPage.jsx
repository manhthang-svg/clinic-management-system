import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';
import { AlertTriangle, ArrowRight, DoorOpen, FileText, Printer, RefreshCw, X } from 'lucide-react';
import defaultPatientApi from '../../apis/patientApi';
import defaultVisitApi from '../../apis/visitApi';
import { AccountPageHeader, FormSection } from '../../components/accounts/AccountFormParts';
import { INPUT_CLASS, INLINE_LABEL_CLASS, Field, FormAlert } from '../../components/shared/FormFields';
import {
  VisitAppointmentCard,
  VisitConfirmModal,
  VisitInsuranceField,
  VisitPatientCard,
  VisitRoomPicker,
  VisitTicket,
} from '../../components/visits';
import {
  EMPTY_VISIT_FORM,
  NO_INSURANCE_CHECK,
  clinicToday,
  formToVisitRequest,
  isCardNumberFormatValid,
  referralSourceOf,
  validateVisitForm,
} from '../../components/visits/visitForm';
import { needsPaymentGuide } from '../../components/visits/visitFormat';
import {
  MSG17,
  insuranceErrorKind,
  loadErrorMessage,
  ticketErrorMessage,
  msg21,
  msg22,
  msg56,
  visitCreateError,
} from '../../components/visits/visitErrors';
import {
  PRIORITY_REASON_OPTIONS,
  REFERRAL_SOURCE_LABELS,
  VISIT_STATUS_LABELS,
  VISIT_TYPE_LABELS,
} from '../../constants/visitData';
import { ROUTES } from '../../routes/routes';

const { RECEPTIONIST } = ROUTES;

// UC-42 step 3: queue counts refresh at least every 10 seconds (UI-04).
const QUEUE_REFRESH_MS = 10000;

/**
 * UC-40 step 8: per room, the number of patients the doctor still has to examine, i.e. today's visits of the room
 * that have a queue number and have not ended. From the UC-42 queue-status summary: every unfinished status except
 * "Chờ thanh toán" (no queue number yet), skipped visits included (they keep their number).
 */
function countByRoom(statuses) {
  const counts = {};
  statuses.forEach((status) => {
    counts[status.roomId] = (status.waitingCount ?? 0) + (status.inExaminationCount ?? 0)
      + (status.waitingParaclinicalCount ?? 0) + (status.waitingConclusionCount ?? 0) + (status.skippedCount ?? 0);
  });
  return counts;
}

function StatusCard({ title, message, actionLabel, onAction, secondaryLabel, onSecondary }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
      <div className="mx-auto w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
        <AlertTriangle className="w-6 h-6" />
      </div>
      <h2 className="text-lg! font-bold! text-[#1b2533] mt-4">{title}</h2>
      <p role="alert" className="text-sm text-slate-500 mt-1 max-w-xl mx-auto">{message}</p>
      <div className="mt-5 flex flex-wrap justify-center gap-3">
        {secondaryLabel && (
          <button
            type="button"
            onClick={onSecondary}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl cursor-pointer"
          >
            <Printer className="w-4 h-4" />
            {secondaryLabel}
          </button>
        )}
        <button
          type="button"
          onClick={onAction}
          className="px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
        >
          {actionLabel}
        </button>
      </div>
    </section>
  );
}

/** A required radio group laid out like an inline field: label on the left, options in one row. */
function RadioRow({ name, label, options, value, onChange, error, disabled = false, hint }) {
  return (
    <fieldset disabled={disabled} className="flex flex-col md:flex-row gap-1.5 md:gap-4">
      <legend className="sr-only">{label}</legend>
      <span aria-hidden="true" className={`block text-sm font-semibold text-slate-700 ${INLINE_LABEL_CLASS}`}>
        {label}<span className="text-rose-500 ml-0.5">*</span>
      </span>
      <div className="flex-1 min-w-0 space-y-1.5">
        <div className="flex flex-wrap gap-3">
          {Object.entries(options).map(([optionValue, optionLabel]) => (
            <label
              key={optionValue}
              className={`flex items-center gap-2.5 px-4 py-2.5 rounded-xl border text-sm font-semibold transition-colors ${
                disabled ? 'cursor-not-allowed' : 'cursor-pointer'
              } ${disabled && value !== optionValue ? 'opacity-50' : ''} ${
                value === optionValue
                  ? 'border-[#127e7a] bg-[#f0f9f8] text-[#127e7a] ring-2 ring-[#127e7a]/15'
                  : error
                    ? 'border-rose-300 bg-rose-50/60 text-slate-700'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              <input
                type="radio"
                name={name}
                value={optionValue}
                checked={value === optionValue}
                onChange={() => onChange(optionValue)}
                className={`w-4 h-4 accent-[#127e7a] ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
              />
              {optionLabel}
            </label>
          ))}
        </div>
        {hint && <p className="text-xs text-slate-500">{hint}</p>}
        {error && <p className="text-xs font-medium text-rose-600">{error}</p>}
      </div>
    </fieldset>
  );
}

/** UC-40 Register Examination (includes UC-41, UC-42; extended by UC-74). */
export function ReceptionistVisitRegisterPage({ patientApi = defaultPatientApi, visitApi = defaultVisitApi }) {
  const { id: patientId } = useParams();
  const navigate = useNavigate();
  const today = clinicToday();

  const [patientState, setPatientState] = useState({ id: null, patient: null, error: null });
  // null until the queue status is loaded; failed when no answer has come yet.
  const [roomStatuses, setRoomStatuses] = useState({ list: null, failed: false });
  const [activeVisitState, setActiveVisitState] = useState({ loaded: false, visit: null });
  const [queueKey, setQueueKey] = useState(0);
  const [rooms, setRooms] = useState({ loading: true, list: [], error: null });
  const [roomsKey, setRoomsKey] = useState(0);
  const [appointment, setAppointment] = useState(null);
  const [appointmentKey, setAppointmentKey] = useState(0);

  const [form, setForm] = useState(EMPTY_VISIT_FORM);
  const [insurance, setInsurance] = useState(NO_INSURANCE_CHECK);
  const [touched, setTouched] = useState(false);
  const [typeNotice, setTypeNotice] = useState(null);
  const [roomNotice, setRoomNotice] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [created, setCreated] = useState(null);

  const goToPatients = useCallback(() => navigate(RECEPTIONIST.PATIENTS), [navigate]);

  useEffect(() => {
    const controller = new AbortController();
    patientApi.detail(patientId, controller.signal)
      .then((patient) => setPatientState({ id: patientId, patient, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setPatientState({ id: patientId, patient: null, error });
      });
    return () => controller.abort();
  }, [patientApi, patientId]);

  // UC-42 queue counts per room for the room picker. They do not hold the form back: the counts fill in when ready.
  useEffect(() => {
    const controller = new AbortController();
    visitApi.getQueueStatus(controller.signal)
      .then((list) => setRoomStatuses({ list: Array.isArray(list) ? list : [], failed: false }))
      .catch((error) => {
        // Keep the last counts on a failed refresh; without any, the picker says the counts are unavailable.
        if (error?.name !== 'AbortError') setRoomStatuses((prev) => ({ ...prev, failed: true }));
      });
    return () => controller.abort();
  }, [visitApi, queueKey]);

  useEffect(() => {
    const controller = new AbortController();
    visitApi.getActiveVisit(patientId, controller.signal)
      .then((visit) => setActiveVisitState({ loaded: true, visit: visit ?? null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setActiveVisitState({ loaded: true, visit: null });
      });
    return () => controller.abort();
  }, [visitApi, patientId, queueKey]);

  useEffect(() => {
    if (created) return undefined;
    const timer = setInterval(() => setQueueKey((key) => key + 1), QUEUE_REFRESH_MS);
    return () => clearInterval(timer);
  }, [created]);

  useEffect(() => {
    const controller = new AbortController();
    visitApi.getActiveExaminationRooms(controller.signal)
      .then((list) => setRooms({ loading: false, list: Array.isArray(list) ? list : [], error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setRooms({ loading: false, list: [], error: loadErrorMessage(error) });
      });
    return () => controller.abort();
  }, [visitApi, roomsKey]);

  // TBD endpoint: a failed lookup is treated as "no appointment".
  useEffect(() => {
    const controller = new AbortController();
    visitApi.getFollowUpAppointment(patientId, controller.signal)
      .then((data) => setAppointment(data?.status === 'SCHEDULED' ? data : null))
      .catch((error) => {
        if (error.name !== 'AbortError') setAppointment(null);
      });
    return () => controller.abort();
  }, [visitApi, patientId, appointmentKey]);

  const patient = patientState.id === patientId ? patientState.patient : null;
  const patientError = patientState.id === patientId ? patientState.error : null;
  const activeVisit = activeVisitState.visit;
  const counts = useMemo(
    () => (roomStatuses.list ? countByRoom(roomStatuses.list) : null),
    [roomStatuses.list],
  );
  const selectedRoom = rooms.list.find((room) => room.id === form.roomId);

  const errors = validateVisitForm(form, insurance);
  const shownErrors = touched ? errors : {};
  const intakeType = form.followUpAppointmentId ? 'FOLLOW_UP' : 'EXAMINATION';

  const update = (changes) => setForm((prev) => ({ ...prev, ...changes }));

  const changeVisitType = (visitType) => {
    update({ visitType });
    setTypeNotice(null);
  };

  // AF-02: an invalid card turns the visit into a self-paid one.
  const switchToSelfPaid = (notice) => {
    update({ visitType: 'DV' });
    setInsurance(NO_INSURANCE_CHECK);
    setTypeNotice(notice);
  };

  const verifyCard = async () => {
    const { cardNumber } = form;
    if (!isCardNumberFormatValid(cardNumber)) return;
    setInsurance({ cardNumber, status: 'VERIFYING', card: null });
    // Ignore an answer for a card number that has since been edited.
    const settle = (next) => setInsurance((prev) => (prev.cardNumber === cardNumber ? next : prev));

    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10000);
    try {
      const card = await visitApi.verifyInsurance({ insuranceCode: cardNumber, patientId }, controller.signal);
      if (card?.status === 'MISMATCH') {
        settle({ cardNumber, status: 'MISMATCH', card });
        return;
      }
      if (card?.status !== 'VALID') {
        switchToSelfPaid(MSG17);
        return;
      }
      settle({ cardNumber, status: 'VALID', card });
    } catch (error) {
      if (error?.name === 'AbortError') {
        settle({ cardNumber, status: 'UNAVAILABLE', card: null });
        return;
      }
      const kind = insuranceErrorKind(error);
      if (kind === 'invalid') switchToSelfPaid(MSG17);
      else if (kind === 'mismatch') settle({ cardNumber, status: 'MISMATCH', card: null });
      else settle({ cardNumber, status: 'ERROR', card: null, errorMessage: loadErrorMessage(error) });
    } finally {
      window.clearTimeout(timeout);
    }
  };

  const attachAppointment = () => {
    const suggested = rooms.list.some((room) => room.id === appointment.suggestedRoomId);
    update({
      followUpAppointmentId: appointment.id,
      followUpAppointmentVersion: appointment.version,
      ...(suggested ? { roomId: appointment.suggestedRoomId } : {}),
    });
  };

  const openConfirm = async () => {
    setTouched(true);
    if (Object.keys(errors).length > 0) {
      const firstErrorId = {
        priorityReason: 'visit-priority-reason',
        cardNumber: 'visit-card-number',
        reason: 'visit-reason',
      }[Object.keys(errors)[0]];
      requestAnimationFrame(() => {
        if (firstErrorId) document.getElementById(firstErrorId)?.focus();
        else if (errors.roomId) document.querySelector('input[name="visit-room"]')?.focus();
      });
      return;
    }

    setConfirm({ fee: null, feeLoading: true, submitting: false, error: null });
    let fee = null;
    try {
      fee = await visitApi.feePreview({
        patientId,
        roomId: form.roomId,
        doituongbnLoai: form.visitType,
        mabhyt: form.visitType === 'BHYT' ? form.cardNumber : undefined,
      });
    } catch {
      // The preview is advisory; visit creation recalculates the authoritative fee.
    }
    setConfirm((prev) => prev && { ...prev, fee, feeLoading: false });
  };

  const register = async () => {
    setConfirm((prev) => ({ ...prev, submitting: true, error: null }));
    try {
      const visit = await visitApi.create(formToVisitRequest(form, patientId, insurance));
      // MSG21; a visit that must pay first has no queue number yet (I1-D48).
      toast.success(msg21(visit));
      setCreated({ visit, ticket: null, loading: true, error: null });
      setConfirm(null);
      try {
        const ticket = await visitApi.getTicket(visit.id);
        setCreated((current) => current?.visit.id === visit.id
          ? { ...current, ticket, loading: false, error: null }
          : current);
      } catch (ticketError) {
        setCreated((current) => current?.visit.id === visit.id
          ? { ...current, loading: false, error: ticketErrorMessage(ticketError) }
          : current);
      }
    } catch (error) {
      const result = visitCreateError(error);
      if (result.kind === 'activeVisit') {
        // EF-01: the screen reloads the active visit and shows MSG22.
        setConfirm(null);
        setQueueKey((key) => key + 1);
      } else if (result.kind === 'room') {
        // EF-03: back to the room choice with a fresh list.
        setConfirm(null);
        setRoomNotice(msg56(selectedRoom?.name));
        update({ roomId: '' });
        setRoomsKey((key) => key + 1);
      } else if (result.kind === 'appointment') {
        // EF-04: back to the appointment card, reloaded.
        setConfirm(null);
        setRoomNotice(null);
        update({ followUpAppointmentId: null, followUpAppointmentVersion: null });
        setAppointmentKey((key) => key + 1);
        toast.error(result.message);
      } else if (result.kind === 'insurance') {
        setConfirm(null);
        switchToSelfPaid(result.message);
      } else {
        // EF-05: stay on the confirmation.
        setConfirm((prev) => ({ ...prev, submitting: false, error: result.message }));
      }
    }
  };

  // UC-41 AF-02: the patient already has an open visit, e.g. the Receptionist left the ticket screen; show its
  // ticket again so it can be reprinted.
  const openActiveTicket = async () => {
    if (!activeVisit) return;
    const visit = { id: activeVisit.id, sovaovien: activeVisit.sovaovien, status: activeVisit.status };
    setCreated({ visit, ticket: null, loading: true, error: null, reprint: true });
    try {
      const ticket = await visitApi.getTicket(visit.id);
      setCreated((current) => current?.visit.id === visit.id ? { ...current, ticket, loading: false } : current);
    } catch (error) {
      setCreated((current) => current?.visit.id === visit.id
        ? { ...current, loading: false, error: ticketErrorMessage(error) }
        : current);
    }
  };

  const reloadTicket = async () => {
    if (!created) return;
    setCreated((current) => ({ ...current, loading: true, error: null }));
    try {
      const ticket = await visitApi.getTicket(created.visit.id);
      setCreated((current) => ({ ...current, ticket, loading: false, error: null }));
    } catch (error) {
      setCreated((current) => ({ ...current, loading: false, error: ticketErrorMessage(error) }));
    }
  };

  const printTicket = async () => {
    if (!created?.ticket) return;
    // A payment guide is not a ticket print, so the Cashier's first ticket is not marked "IN LẠI".
    if (needsPaymentGuide(created.ticket)) {
      window.print();
      return;
    }
    setCreated((current) => ({ ...current, loading: true, error: null }));
    try {
      const ticket = await visitApi.prepareTicketPrint(created.visit.id);
      setCreated((current) => ({ ...current, ticket, loading: false, error: null }));
      window.setTimeout(() => window.print(), 0);
    } catch (error) {
      setCreated((current) => ({ ...current, loading: false, error: ticketErrorMessage(error) }));
    }
  };

  const header = (
    <AccountPageHeader
      title="Đăng ký khám"
      description="Đăng ký lượt khám mới cho bệnh nhân, kể cả lượt tái khám"
      crumbs={[{ label: 'Quản lý bệnh nhân', onClick: goToPatients }, { label: 'Đăng ký khám' }]}
    />
  );

  let content;
  if (created) {
    content = (
      <VisitTicket
        title={created.reprint ? 'In lại phiếu khám' : undefined}
        visit={created.visit}
        ticket={created.ticket}
        loading={created.loading}
        error={created.error}
        onReload={reloadTicket}
        onPrint={printTicket}
        onDone={goToPatients}
      />
    );
  } else if (patientError) {
    const notFound = patientError.status === 404 || patientError.status === 400;
    content = (
      <StatusCard
        title={notFound ? 'Không tìm thấy bệnh nhân' : 'Không tải được hồ sơ bệnh nhân'}
        message={notFound ? 'Hồ sơ bệnh nhân không tồn tại hoặc đường dẫn không đúng.' : loadErrorMessage(patientError)}
        actionLabel="Về danh sách bệnh nhân"
        onAction={goToPatients}
      />
    );
  } else if (!patient || !activeVisitState.loaded) {
    content = <p role="status" className="p-10 text-center text-sm text-slate-400">Đang tải thông tin bệnh nhân…</p>;
  } else if (activeVisit) {
    // EF-01 / MSG22
    content = (
      <StatusCard
        title="Không thể đăng ký khám"
        message={msg22(activeVisit)}
        actionLabel="Về danh sách bệnh nhân"
        onAction={goToPatients}
        secondaryLabel="In lại phiếu khám"
        onSecondary={() => void openActiveTicket()}
      />
    );
  } else {
    const alert = touched && Object.keys(errors).length > 0 ? 'Vui lòng sửa các ô được đánh dấu.' : null;
    content = (
      <>
        <VisitPatientCard patient={patient} intakeType={intakeType} />

        {appointment && (
          <VisitAppointmentCard
            key={appointment.id}
            appointment={appointment}
            today={today}
            attached={form.followUpAppointmentId === appointment.id}
            onAttach={attachAppointment}
            onDetach={() => update({ followUpAppointmentId: null, followUpAppointmentVersion: null })}
          />
        )}

        <div className="space-y-5">
            <FormSection icon={FileText} title="Thông tin lượt khám" description="Đối tượng, lý do khám và nơi giới thiệu">
              <div className="space-y-5">
                <div className="flex flex-col md:flex-row md:items-start gap-3 md:gap-4">
                  <label className={`inline-flex items-center gap-2.5 text-sm font-semibold text-slate-700 cursor-pointer ${INLINE_LABEL_CLASS}`}>
                    <input
                      type="checkbox"
                      checked={form.isPriority}
                      onChange={(e) => update({ isPriority: e.target.checked, priorityReason: '' })}
                      className="w-4 h-4 rounded border-slate-300 accent-[#127e7a] cursor-pointer"
                    />
                    Ưu tiên
                  </label>
                  {form.isPriority && (
                    <Field
                      id="visit-priority-reason"
                      label="Lý do ưu tiên"
                      required
                      inline
                      error={shownErrors.priorityReason}
                      className="flex-1 md:max-w-xl"
                    >
                      <select
                        id="visit-priority-reason"
                        value={form.priorityReason}
                        onChange={(e) => update({ priorityReason: e.target.value })}
                        aria-invalid={Boolean(shownErrors.priorityReason)}
                        aria-describedby={shownErrors.priorityReason ? 'visit-priority-reason-error' : undefined}
                        className={`${INPUT_CLASS} cursor-pointer`}
                      >
                        <option value="">Chọn lý do</option>
                        {PRIORITY_REASON_OPTIONS.map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </Field>
                  )}
                </div>

                <RadioRow
                  name="visit-type"
                  label="Loại lượt khám"
                  options={VISIT_TYPE_LABELS}
                  value={form.visitType}
                  onChange={changeVisitType}
                  error={shownErrors.visitType}
                />

                {typeNotice && (
                  <div role="alert" className="flex items-start gap-2.5 p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 md:ml-44">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                    <p className="text-sm flex-1">{typeNotice} Vui lòng thông báo cho bệnh nhân.</p>
                    <button type="button" aria-label="Đóng thông báo" onClick={() => setTypeNotice(null)} className="p-0.5 rounded hover:bg-amber-100 cursor-pointer">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                )}

                {form.visitType === 'BHYT' && (
                  <VisitInsuranceField
                    inline
                    value={form.cardNumber}
                    onChange={(cardNumber) => update({ cardNumber })}
                    insurance={insurance}
                    error={shownErrors.cardNumber}
                    onVerify={verifyCard}
                    onSwitchToSelfPaid={() => switchToSelfPaid(null)}
                  />
                )}

                <Field
                  id="visit-reason"
                  label="Lý do khám"
                  required
                  inline
                  error={shownErrors.reason}
                  hint={`${form.reason.trim().length} ký tự`}
                >
                  <textarea
                    id="visit-reason"
                    rows={3}
                    value={form.reason}
                    onChange={(e) => update({ reason: e.target.value })}
                    placeholder="VD: Đau đầu, sốt nhẹ 2 ngày"
                    aria-invalid={Boolean(shownErrors.reason)}
                    aria-describedby={shownErrors.reason ? 'visit-reason-error' : undefined}
                    className={`${INPUT_CLASS} resize-y md:max-w-3xl`}
                  />
                </Field>

                <RadioRow
                  name="visit-referral"
                  label="Nơi giới thiệu"
                  options={REFERRAL_SOURCE_LABELS}
                  value={referralSourceOf(form)}
                  onChange={(referralSource) => update({ referralSource })}
                  disabled={intakeType === 'FOLLOW_UP'}
                  hint={intakeType === 'FOLLOW_UP' ? 'Lượt tái khám theo lịch hẹn: nơi giới thiệu là Tự đến.' : undefined}
                />
                {/* BR-129: the referring facility's diagnosis is entered by the Doctor in the examination, not here. */}
              </div>
            </FormSection>

            <FormSection
              icon={DoorOpen}
              title="Phòng khám"
              description="Phòng khám đang hoạt động và số bệnh nhân hôm nay"
              action={
                <button
                  type="button"
                  onClick={() => {
                    setRoomsKey((key) => key + 1);
                    setQueueKey((key) => key + 1);
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50 cursor-pointer"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Làm mới
                </button>
              }
            >
              <div className="space-y-3">
                {roomNotice && <FormAlert>{roomNotice}</FormAlert>}
                <VisitRoomPicker
                  rooms={rooms.list}
                  counts={counts}
                  countsFailed={roomStatuses.failed && !roomStatuses.list}
                  value={form.roomId}
                  onChange={(roomId) => {
                    update({ roomId });
                    setRoomNotice(null);
                  }}
                  error={shownErrors.roomId}
                  loading={rooms.loading}
                  loadError={rooms.error}
                  onReload={() => setRoomsKey((key) => key + 1)}
                  suggestedRoomId={appointment?.suggestedRoomId}
                />
              </div>
            </FormSection>

            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-end gap-3">
              {alert && <div className="sm:mr-auto"><FormAlert>{alert}</FormAlert></div>}
              <button
                type="button"
                onClick={goToPatients}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
              >
                <X className="w-4 h-4" />
                Hủy
              </button>
              <button
                type="button"
                onClick={openConfirm}
                disabled={insurance.status === 'VERIFYING'}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
              >
                Tiếp tục
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
        </div>

        {confirm && (
          <VisitConfirmModal
            patient={patient}
            form={form}
            insurance={insurance}
            room={selectedRoom}
            fee={confirm.fee}
            feeLoading={confirm.feeLoading}
            submitting={confirm.submitting}
            error={confirm.error}
            onBack={() => setConfirm(null)}
            onCancel={goToPatients}
            onConfirm={register}
          />
        )}
      </>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1600px] space-y-6">
      {header}
      {content}
    </div>
  );
}

export default ReceptionistVisitRegisterPage;
