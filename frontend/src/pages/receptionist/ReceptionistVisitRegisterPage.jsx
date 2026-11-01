import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';
import { AlertTriangle, ArrowRight, DoorOpen, FileText, RefreshCw, ShieldPlus, X } from 'lucide-react';
import defaultPatientApi from '../../apis/patientApi';
import defaultVisitApi from '../../apis/visitApi';
import { AccountPageHeader, FormSection } from '../../components/accounts/AccountFormParts';
import { INPUT_CLASS, Field, FormAlert } from '../../components/shared/FormFields';
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
  MAX_REASON_LENGTH,
  NO_INSURANCE_CHECK,
  clinicToday,
  formToVisitRequest,
  isCardNumberFormatValid,
  validateVisitForm,
} from '../../components/visits/visitForm';
import {
  MSG17,
  insuranceErrorKind,
  loadErrorMessage,
  msg22,
  msg56,
  visitCreateError,
} from '../../components/visits/visitErrors';
import { roomLabel } from '../../components/visits/visitFormat';
import {
  PRIORITY_REASON_LABELS,
  REFERRAL_SOURCE_LABELS,
  VISIT_STATUS_LABELS,
  VISIT_TYPE_LABELS,
} from '../../constants/visitData';
import { ROUTES } from '../../routes/routes';

const { RECEPTIONIST } = ROUTES;

// UC-42 step 3: queue counts refresh at least every 10 seconds (UI-04).
const QUEUE_REFRESH_MS = 10000;

function countByRoom(visits) {
  const counts = {};
  visits.forEach((visit) => {
    const room = (counts[visit.roomId] ??= { waiting: 0, inExamination: 0 });
    if (visit.status === 'WAITING') room.waiting += 1;
    if (visit.status === 'IN_EXAMINATION') room.inExamination += 1;
  });
  return counts;
}

function StatusCard({ title, message, actionLabel, onAction }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
      <div className="mx-auto w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
        <AlertTriangle className="w-6 h-6" />
      </div>
      <h2 className="text-lg! font-bold! text-[#1b2533] mt-4">{title}</h2>
      <p role="alert" className="text-sm text-slate-500 mt-1 max-w-xl mx-auto">{message}</p>
      <button
        type="button"
        onClick={onAction}
        className="mt-5 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
      >
        {actionLabel}
      </button>
    </section>
  );
}

/** UC-40 Register Examination (includes UC-41, UC-42; extended by UC-74). */
export function ReceptionistVisitRegisterPage({ patientApi = defaultPatientApi, visitApi = defaultVisitApi }) {
  const { id: patientId } = useParams();
  const navigate = useNavigate();
  const today = clinicToday();

  const [patientState, setPatientState] = useState({ id: null, patient: null, error: null });
  const [todayVisits, setTodayVisits] = useState({ loaded: false, list: [] });
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

  // Today's visits give the BR-17 check and the UC-42 queue counts.
  // ASSUMPTION: an active visit is always from today, since BR-22 closes visits at day end.
  useEffect(() => {
    const controller = new AbortController();
    visitApi.getQueue({ date: today }, controller.signal)
      .then((list) => setTodayVisits({ loaded: true, list: Array.isArray(list) ? list : [] }))
      .catch((error) => {
        if (error.name !== 'AbortError') setTodayVisits((prev) => ({ ...prev, loaded: true }));
      });
    return () => controller.abort();
  }, [visitApi, today, queueKey]);

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
  const counts = useMemo(() => countByRoom(todayVisits.list), [todayVisits.list]);
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

  const cancelAppointment = async (reason) => {
    await visitApi.cancelFollowUpAppointment(appointment.id, reason, appointment.version);
    toast.success('Đã hủy lịch hẹn tái khám');
    update({ followUpAppointmentId: null, followUpAppointmentVersion: null });
    setAppointment(null);
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
      // MSG21
      toast.success(`Đã tạo lượt khám ${visit.sovaovien}. Số thứ tự ${visit.queueNumber} – ${visit.roomName ?? selectedRoom?.name}.`);
      setCreated({ visit, ticket: null, loading: true, error: null });
      setConfirm(null);
      try {
        const ticket = await visitApi.getTicket(visit.id);
        setCreated((current) => current?.visit.id === visit.id
          ? { ...current, ticket, loading: false, error: null }
          : current);
      } catch (ticketError) {
        setCreated((current) => current?.visit.id === visit.id
          ? { ...current, loading: false, error: loadErrorMessage(ticketError) }
          : current);
      }
    } catch (error) {
      const result = visitCreateError(error);
      if (result.kind === 'activeVisit') {
        // EF-01: the screen reloads today's visits and shows MSG22.
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

  const reloadTicket = async () => {
    if (!created) return;
    setCreated((current) => ({ ...current, loading: true, error: null }));
    try {
      const ticket = await visitApi.getTicket(created.visit.id);
      setCreated((current) => ({ ...current, ticket, loading: false, error: null }));
    } catch (error) {
      setCreated((current) => ({ ...current, loading: false, error: loadErrorMessage(error) }));
    }
  };

  const printTicket = async () => {
    if (!created?.ticket) return;
    setCreated((current) => ({ ...current, loading: true, error: null }));
    try {
      const ticket = await visitApi.prepareTicketPrint(created.visit.id);
      setCreated((current) => ({ ...current, ticket, loading: false, error: null }));
      window.setTimeout(() => window.print(), 0);
    } catch (error) {
      setCreated((current) => ({ ...current, loading: false, error: loadErrorMessage(error) }));
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
  } else if (!patient || !todayVisits.loaded || !activeVisitState.loaded) {
    content = <p role="status" className="p-10 text-center text-sm text-slate-400">Đang tải thông tin bệnh nhân…</p>;
  } else if (activeVisit) {
    // EF-01 / MSG22
    content = (
      <StatusCard
        title="Không thể đăng ký khám"
        message={msg22(activeVisit, VISIT_STATUS_LABELS[activeVisit.status] ?? activeVisit.status)}
        actionLabel="Về danh sách bệnh nhân"
        onAction={goToPatients}
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
            onCancel={cancelAppointment}
          />
        )}

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_300px] gap-5 items-start">
          <div className="space-y-5">
            <FormSection icon={ShieldPlus} title="Đối tượng khám" description="Ưu tiên và loại lượt khám">
              <div className="space-y-5">
                <div className="space-y-3">
                  <label className="inline-flex items-center gap-2.5 text-sm font-semibold text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={form.isPriority}
                      onChange={(e) => update({ isPriority: e.target.checked, priorityReason: '' })}
                      className="w-4 h-4 rounded border-slate-300 accent-[#127e7a] cursor-pointer"
                    />
                    Ưu tiên
                  </label>
                  {form.isPriority && (
                    <Field id="visit-priority-reason" label="Lý do ưu tiên" required error={shownErrors.priorityReason} className="md:w-1/2">
                      <select
                        id="visit-priority-reason"
                        value={form.priorityReason}
                        onChange={(e) => update({ priorityReason: e.target.value })}
                        aria-invalid={Boolean(shownErrors.priorityReason)}
                        aria-describedby={shownErrors.priorityReason ? 'visit-priority-reason-error' : undefined}
                        className={`${INPUT_CLASS} cursor-pointer`}
                      >
                        <option value="">Chọn lý do</option>
                        {Object.entries(PRIORITY_REASON_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </Field>
                  )}
                </div>

                <fieldset className="space-y-2">
                  <legend className="block text-sm font-semibold text-slate-700 mb-1.5">
                    Loại lượt khám<span className="text-rose-500 ml-0.5">*</span>
                  </legend>
                  <div className="grid grid-cols-2 gap-3 md:w-1/2">
                    {Object.entries(VISIT_TYPE_LABELS).map(([value, label]) => (
                      <label
                        key={value}
                        className={`flex items-center gap-2.5 px-4 py-3 rounded-xl border text-sm font-semibold cursor-pointer transition-colors ${
                          form.visitType === value
                            ? 'border-[#127e7a] bg-[#f0f9f8] text-[#127e7a] ring-2 ring-[#127e7a]/15'
                            : shownErrors.visitType
                              ? 'border-rose-300 bg-rose-50/60 text-slate-700'
                              : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                        }`}
                      >
                        <input
                          type="radio"
                          name="visit-type"
                          value={value}
                          checked={form.visitType === value}
                          onChange={() => changeVisitType(value)}
                          className="w-4 h-4 accent-[#127e7a] cursor-pointer"
                        />
                        {label}
                      </label>
                    ))}
                  </div>
                  {shownErrors.visitType && <p className="text-xs font-medium text-rose-600">{shownErrors.visitType}</p>}
                </fieldset>

                {typeNotice && (
                  <div role="alert" className="flex items-start gap-2.5 p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                    <p className="text-sm flex-1">{typeNotice} Vui lòng thông báo cho bệnh nhân.</p>
                    <button type="button" aria-label="Đóng thông báo" onClick={() => setTypeNotice(null)} className="p-0.5 rounded hover:bg-amber-100 cursor-pointer">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                )}

                {form.visitType === 'BHYT' && (
                  <VisitInsuranceField
                    value={form.cardNumber}
                    onChange={(cardNumber) => update({ cardNumber })}
                    insurance={insurance}
                    error={shownErrors.cardNumber}
                    onVerify={verifyCard}
                    onSwitchToSelfPaid={() => switchToSelfPaid(null)}
                  />
                )}
              </div>
            </FormSection>

            <FormSection icon={FileText} title="Lý do khám" description="Lý do đến khám và nơi giới thiệu">
              <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_240px] gap-5">
                <Field
                  id="visit-reason"
                  label="Lý do khám"
                  required
                  error={shownErrors.reason}
                  hint={`${form.reason.trim().length}/${MAX_REASON_LENGTH} ký tự`}
                >
                  <textarea
                    id="visit-reason"
                    rows={3}
                    value={form.reason}
                    onChange={(e) => update({ reason: e.target.value })}
                    placeholder="VD: Đau đầu, sốt nhẹ 2 ngày"
                    aria-invalid={Boolean(shownErrors.reason)}
                    aria-describedby={shownErrors.reason ? 'visit-reason-error' : undefined}
                    className={`${INPUT_CLASS} resize-none`}
                  />
                </Field>
                <Field id="visit-referral" label="Nơi giới thiệu">
                  <select
                    id="visit-referral"
                    value={form.referralSource}
                    onChange={(e) => update({ referralSource: e.target.value })}
                    className={`${INPUT_CLASS} cursor-pointer`}
                  >
                    {Object.entries(REFERRAL_SOURCE_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </Field>
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
          </div>

          <aside className="space-y-5 xl:sticky xl:top-4">
            <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-3">
              <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">Tóm tắt</h2>
              <dl className="space-y-2.5 text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-slate-500">Ưu tiên</dt>
                  <dd className="font-semibold text-slate-800 text-right">
                    {form.isPriority ? PRIORITY_REASON_LABELS[form.priorityReason] ?? 'Chưa chọn lý do' : 'Không'}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-slate-500">Loại lượt khám</dt>
                  <dd className="font-semibold text-slate-800 text-right">{VISIT_TYPE_LABELS[form.visitType] ?? '—'}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-slate-500">Phòng khám</dt>
                  <dd className="font-semibold text-slate-800 text-right">{selectedRoom ? roomLabel(selectedRoom) : '—'}</dd>
                </div>
              </dl>
            </section>

            <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-2.5">
              <button
                type="button"
                onClick={openConfirm}
                disabled={insurance.status === 'VERIFYING'}
                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
              >
                Tiếp tục
                <ArrowRight className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={goToPatients}
                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
              >
                <X className="w-4 h-4" />
                Hủy
              </button>
              {alert && <FormAlert>{alert}</FormAlert>}
            </div>
          </aside>
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
