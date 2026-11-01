import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { CalendarClock, Hospital, Info, Lock, RotateCcw, Save, TriangleAlert } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { ConfirmModal } from '../../components/ui';
import { Field, FormAlert, INPUT_CLASS } from '../../components/shared/FormFields';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { clinicFacilityApi } from '../../apis/clinicFacilityApi';
import {
  FIELD_LABEL,
  ORGANIZATION_FORMS,
  changedFields,
  facilityError,
  facilityToForm,
  formToRequest,
  needsLicenceReminder,
  validateFacilityForm,
} from '../../components/clinicFacility/facilityForm';

const { ADMIN } = ROUTES;

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function errorProps(id, error) {
  return error ? { 'aria-invalid': true, 'aria-describedby': `${id}-error` } : {};
}

/** UC-77 Update Hospital Information. */
export function HospitalInformationPage({ currentAdmin, onLogout, onChangePassword, api = clinicFacilityApi }) {
  const navigate = useNavigate();
  const [facility, setFacility] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [form, setForm] = useState(() => facilityToForm(null));
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [serverErrors, setServerErrors] = useState({});
  const [confirmOpen, setConfirmOpen] = useState(false);

  const apply = useCallback((data) => {
    setFacility(data);
    setForm(facilityToForm(data));
    setTouched(false);
    setServerErrors({});
  }, []);

  // State is only set after the request settles, so the effect below never sets it synchronously.
  const load = useCallback(async (signal) => {
    try {
      const data = await api.get(signal);
      setLoadError('');
      apply(data);
    } catch (error) {
      if (error?.name === 'AbortError') return;
      setLoadError(facilityError(error).message);
    }
  }, [api, apply]);

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      await load(controller.signal);
    })();
    return () => controller.abort();
  }, [load]);

  const initialForm = facilityToForm(facility);
  const errors = validateFacilityForm(form);
  const shownErrors = { ...(touched ? errors : {}), ...serverErrors };
  const changed = changedFields(form, initialForm);
  const dirty = changed.length > 0;
  const configured = Boolean(facility?.configured);
  const codeLocked = Boolean(facility?.maCskcbLocked);

  const onChange = (field) => (event) => {
    const { value } = event.target;
    setServerErrors((prev) => {
      if (!(field in prev)) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const save = async () => {
    setConfirmOpen(false);
    setSubmitting(true);
    setSubmitError('');
    try {
      apply(await api.save(formToRequest(form, facility)));
      toast.success(configured ? 'Đã cập nhật thông tin cơ sở.' : 'Đã khai báo thông tin cơ sở.');
    } catch (error) {
      const result = facilityError(error);
      setSubmitError(result.message);
      setServerErrors(result.fields ?? {});
      if (result.reload) {
        try {
          const latest = await api.get();
          setFacility(latest);
          if (error.errorResponse?.code === 'FACILITY_CODE_LOCKED') {
            setForm((prev) => ({ ...prev, maCskcb: latest.maCskcb ?? '' }));
          } else {
            setForm(facilityToForm(latest));
          }
        } catch {
          // Keep the form; the alert already tells the Admin to reload.
        }
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submit = (event) => {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0 || !dirty || submitting) return;
    if (configured && needsLicenceReminder(changed)) {
      setConfirmOpen(true);
      return;
    }
    save();
  };

  const reset = () => {
    setForm(initialForm);
    setTouched(false);
    setServerErrors({});
    setSubmitError('');
  };

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'hospital-information',
  };

  const header = (
    <AccountPageHeader
      title="Thông tin cơ sở khám bệnh, chữa bệnh"
      description="Thông tin này được in trên phiếu khám và các giấy tờ của phòng khám."
      crumbs={[{ label: 'Thông tin bệnh viện' }]}
    />
  );

  if (!facility) {
    return (
      <AdminLayout {...layoutProps}>
        <div className="px-6 pb-8 space-y-6">
          {header}
          {loadError ? (
            <div className="space-y-3 max-w-xl">
              <FormAlert>{loadError}</FormAlert>
              <button
                type="button"
                onClick={() => load()}
                className="px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
              >
                Thử lại
              </button>
            </div>
          ) : (
            <p className="text-sm text-slate-500">Đang tải thông tin cơ sở…</p>
          )}
        </div>
      </AdminLayout>
    );
  }

  const textField = (field, { hint, inputMode, autoComplete, className, disabled } = {}) => {
    const id = `facility-${field}`;
    return (
      <Field id={id} label={FIELD_LABEL[field]} required error={shownErrors[field]} hint={hint} className={className}>
        <input
          id={id}
          value={form[field]}
          onChange={onChange(field)}
          inputMode={inputMode}
          autoComplete={autoComplete ?? 'off'}
          disabled={disabled}
          className={INPUT_CLASS}
          {...errorProps(id, shownErrors[field])}
        />
      </Field>
    );
  };

  const licenceReminder = configured && needsLicenceReminder(changed);

  return (
    <AdminLayout {...layoutProps}>
      <form onSubmit={submit} noValidate className="px-6 pb-8 space-y-6">
        {header}

        {!configured && (
          <div className="flex items-start gap-2.5 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800">
            <Info className="w-4 h-4 shrink-0 mt-0.5" />
            <p className="text-sm">
              Cơ sở chưa được khai báo. Vui lòng nhập thông tin theo giấy phép hoạt động do cơ quan có thẩm quyền cấp.
            </p>
          </div>
        )}

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_320px] gap-5 items-start">
          <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
            <div className="flex items-start gap-3 mb-5">
              <div className="w-10 h-10 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
                <Hospital className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">Thông tin cơ sở</h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Các ô có dấu <span className="text-rose-500">*</span> là bắt buộc.
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4">
              {textField('maCskcb', {
                inputMode: 'numeric',
                disabled: codeLocked,
                hint: codeLocked
                  ? 'Đã khóa vì phòng khám đã có lượt khám.'
                  : 'Gồm 5 chữ số. Sẽ bị khóa sau lượt khám đầu tiên.',
              })}
              {textField('sodienthoai', { inputMode: 'tel', autoComplete: 'tel', hint: 'Ví dụ: 02812345678 hoặc 0901234567' })}
              {textField('tenCskcb', { className: 'md:col-span-2', hint: 'Ghi đúng như trên giấy phép hoạt động.' })}
              {textField('diachi', { className: 'md:col-span-2' })}
              {textField('soGiayPhepHoatDong')}

              <Field
                id="facility-hinhThucToChuc"
                label={FIELD_LABEL.hinhThucToChuc}
                required
                error={shownErrors.hinhThucToChuc}
              >
                <select
                  id="facility-hinhThucToChuc"
                  value={form.hinhThucToChuc}
                  onChange={onChange('hinhThucToChuc')}
                  className={INPUT_CLASS}
                  {...errorProps('facility-hinhThucToChuc', shownErrors.hinhThucToChuc)}
                >
                  <option value="">Chọn hình thức tổ chức</option>
                  {ORGANIZATION_FORMS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </Field>
            </div>

            {licenceReminder && (
              <div className="mt-4 flex items-start gap-2.5 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800">
                <TriangleAlert className="w-4 h-4 shrink-0 mt-0.5" />
                <p className="text-xs leading-5">
                  Thay đổi địa điểm hoặc hình thức tổ chức thường phải được cấp giấy phép hoạt động mới.
                  Vui lòng kiểm tra lại số giấy phép hoạt động.
                </p>
              </div>
            )}
          </section>

          <aside className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-4">
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">Tình trạng</h2>
            <div className="flex items-start gap-3">
              <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${codeLocked ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600'}`}>
                <Lock className="w-4 h-4" />
              </div>
              <div className="text-sm">
                <div className="font-semibold text-slate-800">Mã cơ sở KBCB</div>
                <div className="text-slate-600">{codeLocked ? 'Đã khóa' : 'Còn thay đổi được'}</div>
                <div className="text-xs text-slate-400">
                  {codeLocked
                    ? 'Mã đã được in trên giấy tờ khám bệnh nên không thể đổi.'
                    : 'Chỉ đổi được trước lượt khám đầu tiên.'}
                </div>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 bg-slate-50 text-slate-600">
                <CalendarClock className="w-4 h-4" />
              </div>
              <div className="text-sm">
                <div className="font-semibold text-slate-800">Cập nhật lần cuối</div>
                <div className="text-slate-600">{configured ? formatDateTime(facility.updatedAt) : 'Chưa khai báo'}</div>
              </div>
            </div>
            <p className="text-xs text-slate-500 leading-5 border-t border-slate-100 pt-3">
              Giấy tờ đã in giữ nguyên thông tin tại thời điểm in. Thay đổi chỉ áp dụng cho giấy tờ in sau đó.
            </p>
          </aside>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 flex flex-wrap items-center justify-end gap-2.5 sticky bottom-4">
          {submitError ? (
            <p role="alert" className="text-xs text-rose-600 mr-auto">{submitError}</p>
          ) : touched && Object.keys(errors).length > 0 && (
            <p role="alert" className="text-xs text-rose-600 mr-auto">Vui lòng sửa các ô được đánh dấu.</p>
          )}
          <button
            type="button"
            disabled={!dirty || submitting}
            onClick={reset}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <RotateCcw className="w-4 h-4" />
            Khôi phục
          </button>
          <button
            type="submit"
            disabled={!dirty || submitting}
            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Save className="w-4 h-4" />
            {submitting ? 'Đang lưu…' : configured ? 'Lưu thay đổi' : 'Khai báo cơ sở'}
          </button>
        </div>
      </form>

      <ConfirmModal
        isOpen={confirmOpen}
        title="Kiểm tra giấy phép hoạt động"
        description="Bạn đã thay đổi địa chỉ hoặc hình thức tổ chức. Việc này thường cần giấy phép hoạt động mới. Bạn đã cập nhật số giấy phép hoạt động chưa?"
        confirmText="Vẫn lưu"
        cancelText="Quay lại sửa"
        onConfirm={save}
        onClose={() => setConfirmOpen(false)}
      />
    </AdminLayout>
  );
}

export default HospitalInformationPage;
