import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';
import {
  ChevronRight, ClipboardList, Eye, FileText, House, Lock, Printer, Save, Send, ShieldAlert, Sparkles, UserRound,
} from 'lucide-react';
import { ConfirmModal } from '../ui';
import { ResultPrintView } from './ResultPrintView';
import { ResultApiContext } from './resultContext';
import { Field, INPUT_CLASS } from '../shared/FormFields';
import { PRINT_FORMS } from './resultMockData';
import {
  KIND_LABEL, MSG, formForSave, formatDateTime, resultErrorMessage, resultToForm, templateFor, validateResult,
} from './resultForm';

export function ResultSection({ title, description, action, children }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">{title}</h2>
          {description && <p className="text-xs text-slate-500 mt-0.5">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function Info({ label, children }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-sm font-semibold text-slate-800">{children || '—'}</dd>
    </div>
  );
}

export function ResultBreadcrumb({ title }) {
  const navigate = useNavigate();
  return (
    <div className="space-y-1.5">
      <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-slate-500">
        <House className="w-3.5 h-3.5" />
        <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
        <button type="button" onClick={() => navigate('/technician/results')} className="hover:text-[#127e7a] cursor-pointer">
          Nhập kết quả
        </button>
        <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
        <span className="text-slate-700 font-medium">{title}</span>
      </nav>
      <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">{title}</h1>
    </div>
  );
}

/**
 * Shared frame of every result page (UC-67, UC-68): patient card, read-only banners, files, preview,
 * save draft / submit / print. `children` renders the page's own fields with
 * { form, set, errors, readOnly, template }.
 */
export function ResultEditor({ order: initialOrder, api, children }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [order, setOrder] = useState(initialOrder);

  // Danh sách dịch vụ cùng đợt của bệnh nhân (ưu tiên từ order.siblingServices, sau đó từ location.state)
  const siblingServices = (order.siblingServices && order.siblingServices.length > 0)
    ? order.siblingServices
    : (location.state?.siblingServices || []);

  const handleSwitchTab = (targetId) => {
    if (targetId === order.id) return;
    navigate(`/technician/results/${targetId}`, {
      state: {
        siblingServices,
      },
    });
  };
  const [form, setForm] = useState(() => resultToForm(initialOrder));
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [confirm, setConfirm] = useState(null);
  const [showPreview, setShowPreview] = useState(false);

  const printForm = PRINT_FORMS[order.printForm] ?? PRINT_FORMS.LAB;
  const title = printForm.title.charAt(0) + printForm.title.slice(1).toLowerCase();
  const submitted = order.result?.status === 'SUBMITTED';
  const readOnly = submitted || !order.isPerformer;
  const errors = validateResult(order, form);
  const shownErrors = touched ? errors : {};
  const template = templateFor(order);
  const saveable = formForSave(order, form);

  const set = (field, value) => setForm((prev) => ({ ...prev, [field]: value }));

  const run = async (kind, action) => {
    setBusy(kind);
    setSubmitError('');
    try {
      const saved = await action();
      setOrder(saved);
      setForm(resultToForm(saved));
      return saved;
    } catch (error) {
      setSubmitError(resultErrorMessage(error));
      if (error?.code === 'RESULT_ALREADY_SUBMITTED' || error?.code === 'NOT_PERFORMER') {
        try {
          setOrder(await api.get(order.id));
        } catch {
          // Keep the form; the alert already explains the problem.
        }
      }
      return null;
    } finally {
      setBusy('');
    }
  };

  const saveDraft = async () => {
    if (await run('draft', () => api.saveDraft(order.id, saveable))) toast.success(MSG.draftSaved);
  };

  const askSubmit = () => {
    setTouched(true);
    if (Object.keys(errors).length > 0) {
      setSubmitError(MSG.fixFields);
      return;
    }
    setConfirm('submit');
  };

  const submit = async () => {
    setConfirm(null);
    if (await run('submit', () => api.submit(order.id, saveable))) toast.success(MSG.saved);
  };

  // UC-68: print, then count the print so that the next one is marked "Bản sao" (AF-01).
  // UC-68: only the performer prints a submitted result, and the slip needs the clinic header (null = not configured).
  const printBlocked = !order.isPerformer ? MSG.printerOnly : order.clinic === null ? MSG.clinicMissing : '';
  const print = async () => {
    try {
      window.print();
    } catch {
      toast.error(MSG.printFailed);
      return;
    }
    try {
      setOrder(await api.recordPrint(order.id));
    } catch (error) {
      // Printing already happened; only the copy mark is missing.
      toast.error(resultErrorMessage(error));
    }
  };

  const applyTemplate = () => {
    setConfirm(null);
    setForm((prev) => ({
      ...prev,
      ...(template.sections ? { sections: { ...template.sections } } : {}),
      ...(template.technique ? { technique: template.technique } : {}),
      ...(template.description ? { description: template.description } : {}),
      conclusion: template.conclusion ?? prev.conclusion,
    }));
  };

  const hasContent = form.conclusion?.trim()
    || form.description?.trim()
    || Object.values(form.sections ?? {}).some((value) => value.trim());
  const askTemplate = () => (hasContent ? setConfirm('template') : applyTemplate());

  const templateButton = !readOnly && template && (
    <button
      type="button"
      onClick={askTemplate}
      className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-semibold text-[#127e7a] bg-[#e6f5f3] hover:bg-[#d8efec] rounded-xl cursor-pointer"
    >
      <Sparkles className="w-4 h-4" />
      Điền kết quả bình thường
    </button>
  );

  const { patient } = order;
  const statusBadge = submitted
    ? { label: 'Đã gửi', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' }
    : order.result
      ? { label: 'Bản nháp', className: 'bg-amber-50 text-amber-700 border-amber-200' }
      : { label: 'Chưa nhập', className: 'bg-slate-100 text-slate-600 border-slate-200' };

  return (
    <div className="space-y-6 pb-10">
      <ResultBreadcrumb title={title} />

      {/* Tab chuyển đổi giữa các dịch vụ của bệnh nhân */}
      {siblingServices.length > 1 && (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-3.5 shadow-2xs space-y-2.5">
          <div className="flex items-center justify-between text-xs px-1">
            <div className="flex items-center gap-2 font-bold text-slate-800">
              <span className="w-2 h-2 rounded-full bg-teal-500" />
              <span>Dịch vụ chỉ định cùng đợt ({siblingServices.length} dịch vụ):</span>
            </div>
            <span className="text-[11px] text-slate-400">Chọn tab để chuyển form nhập từng dịch vụ</span>
          </div>

          <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-slate-100">
            {siblingServices.map((svc) => {
              const isActive = svc.id === order.id;
              const isSubmitted = svc.status === 'COMPLETED' || svc.status === 'SUBMITTED';
              const isDraft = svc.status === 'DRAFT';

              return (
                <button
                  key={svc.id}
                  type="button"
                  onClick={() => handleSwitchTab(svc.id)}
                  className={`group relative px-4 py-2.5 rounded-xl text-xs font-bold transition-all flex items-center gap-2.5 cursor-pointer border ${
                    isActive
                      ? 'bg-teal-600 text-white border-teal-600 shadow-sm ring-2 ring-teal-600/20'
                      : 'bg-slate-50 hover:bg-slate-100 text-slate-700 border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <span className="truncate max-w-[280px]">{svc.serviceName}</span>
                  <span
                    className={`px-1.5 py-0.5 rounded text-[10px] font-extrabold ${
                      isActive
                        ? 'bg-teal-700 text-teal-50'
                        : isSubmitted
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : isDraft
                        ? 'bg-sky-50 text-sky-700 border border-sky-200'
                        : 'bg-amber-50 text-amber-700 border border-amber-200'
                    }`}
                  >
                    {isSubmitted ? 'Đã gửi' : isDraft ? 'Bản nháp' : 'Chờ nhập'}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <div className="w-11 h-11 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
              <UserRound className="w-5 h-5" />
            </div>
            <div>
              <div className="text-lg font-extrabold text-[#1b2533]">{patient.name}</div>
              <div className="text-xs text-slate-500">
                {patient.code} · {patient.age} tuổi · {patient.gender} · {patient.coverage}
                {patient.insuranceNumber ? ` ${patient.insuranceNumber}` : ''}
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {order.priority === 'EMERGENCY' && (
              <span className="px-2.5 py-1 rounded-lg border text-xs font-bold bg-rose-50 text-rose-700 border-rose-200">Cấp cứu</span>
            )}
            <span className="px-2.5 py-1 rounded-lg border text-xs font-semibold bg-sky-50 text-sky-700 border-sky-200">{KIND_LABEL[order.kind]}</span>
            <span className={`px-2.5 py-1 rounded-lg border text-xs font-semibold ${statusBadge.className}`}>{statusBadge.label}</span>
          </div>
        </div>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 border-t border-slate-100 pt-4">
          <Info label="Yêu cầu">{order.serviceName}</Info>
          <Info label="Mã phiếu">{order.orderCode}</Info>
          <Info label="Bác sĩ / phòng chỉ định">{order.orderingDoctor} – {order.orderingRoom}</Info>
          <Info label="Người thực hiện">{order.performer?.name}</Info>
          <Info label="Chẩn đoán">{order.diagnosis}</Info>
          <Info label="Ghi chú lâm sàng">{order.symptoms ?? order.clinicalNote}</Info>
          <Info label="Thời gian chỉ định">{formatDateTime(order.orderedAt)}</Info>
          <Info label="Thời gian thực hiện">{formatDateTime(order.performedAt)}</Info>
        </dl>
      </section>

      {!order.isPerformer && (
        <div role="status" className="flex items-start gap-2.5 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800">
          <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
          <p className="text-sm">{MSG.notPerformer}</p>
        </div>
      )}
      {submitted && (
        <div role="status" className="flex items-start gap-2.5 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800">
          <Lock className="w-4 h-4 shrink-0 mt-0.5" />
          <p className="text-sm">{MSG.submitted} Gửi lúc {formatDateTime(order.result.submittedAt)}.</p>
        </div>
      )}

      {(order.instruments?.length > 0 || order.result?.instrumentName) && (
        <ResultSection title="Máy thực hiện" description="Máy đã chạy mẫu hoặc thực hiện dịch vụ (in trên phiếu).">
          <div className="max-w-sm">
            <Field id="result-instrument" label="Máy thực hiện">
              {readOnly ? (
                <p id="result-instrument" className="text-sm font-semibold text-slate-800">{order.result?.instrumentName || '—'}</p>
              ) : (
                <select
                  id="result-instrument"
                  value={form.instrumentId ?? ''}
                  onChange={(event) => set('instrumentId', event.target.value)}
                  className={INPUT_CLASS}
                >
                  <option value="">-- Chọn máy --</option>
                  {order.instruments.map((instrument) => (
                    <option key={instrument.id} value={instrument.id}>{instrument.code} – {instrument.name}</option>
                  ))}
                </select>
              )}
            </Field>
          </div>
        </ResultSection>
      )}

      <ResultApiContext.Provider value={{
        supportsFiles: api?.supportsFiles !== false,
        // anh tai len / xoa ngay tren server cua phieu dang mo
        uploadImage: api?.uploadImage ? (file, caption) => api.uploadImage(order.id, file, caption) : null,
        deleteImage: api?.deleteImage ? (imageId) => api.deleteImage(order.id, imageId) : null,
      }}>
        {children({ order, form, set, errors: shownErrors, readOnly, templateButton })}
      </ResultApiContext.Provider>

      {(submitted || showPreview) && (
        <ResultSection
          title="Xem trước phiếu in"
          description={submitted
            ? (printBlocked || 'Phiếu in khổ A4 đưa cho người bệnh.')
            : 'Bản nháp chỉ để xem trước. Phải gửi kết quả rồi mới in được.'}
          action={submitted && !printBlocked && (
            <button
              type="button"
              onClick={print}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
            >
              <Printer className="w-4 h-4" />
              {order.printCount ? 'In lại (bản sao)' : 'In phiếu kết quả'}
            </button>
          )}
        >
          <div className="border border-slate-200 rounded-xl overflow-auto bg-slate-100 p-4">
            <div className="shadow-md w-fit mx-auto">
              <ResultPrintView order={order} form={saveable} />
            </div>
          </div>
        </ResultSection>
      )}

      <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 flex flex-wrap items-center justify-end gap-2.5 sticky bottom-4">
        {submitError && <p role="alert" className="text-xs text-rose-600 mr-auto">{submitError}</p>}
        {!submitError && order.result?.savedAt && !submitted && (
          <p className="text-xs text-slate-500 mr-auto flex items-center gap-1.5">
            <FileText className="w-3.5 h-3.5" /> Lưu nháp lúc {formatDateTime(order.result.savedAt)}
          </p>
        )}
        <button
          type="button"
          onClick={() => navigate('/technician/results')}
          className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 bg-slate-100 rounded-xl hover:bg-slate-200 cursor-pointer"
        >
          <ClipboardList className="w-4 h-4" />
          Về danh sách
        </button>
        {!submitted && (
          <button
            type="button"
            onClick={() => setShowPreview((value) => !value)}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
          >
            <Eye className="w-4 h-4" />
            {showPreview ? 'Ẩn xem trước' : 'Xem trước phiếu in'}
          </button>
        )}
        {!readOnly && (
          <>
            <button
              type="button"
              onClick={saveDraft}
              disabled={Boolean(busy)}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Save className="w-4 h-4" />
              {busy === 'draft' ? 'Đang lưu…' : 'Lưu nháp'}
            </button>
            <button
              type="button"
              onClick={askSubmit}
              disabled={Boolean(busy)}
              className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Send className="w-4 h-4" />
              {busy === 'submit' ? 'Đang gửi…' : 'Gửi kết quả'}
            </button>
          </>
        )}
      </div>

      <ConfirmModal
        isOpen={confirm === 'submit'}
        title="Gửi kết quả"
        description={MSG.confirmSubmit}
        confirmText="Gửi kết quả"
        cancelText="Quay lại"
        onConfirm={submit}
        onClose={() => setConfirm(null)}
      />
      <ConfirmModal
        isOpen={confirm === 'template'}
        title="Điền kết quả bình thường"
        description="Nội dung đang nhập sẽ được thay bằng kết quả bình thường. Bạn có muốn tiếp tục?"
        confirmText="Điền mẫu"
        cancelText="Giữ nội dung"
        onConfirm={applyTemplate}
        onClose={() => setConfirm(null)}
      />
    </div>
  );
}

export default ResultEditor;
