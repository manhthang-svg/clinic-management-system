import { useId, useState } from 'react';
import { Download, RefreshCw, X } from 'lucide-react';
import { toast } from 'sonner';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { Field, FormAlert, INPUT_CLASS } from '../../../components/shared/FormFields';
import {
  EXPORT_MSG,
  EXPORT_PURPOSES,
  RECIPIENT_MAX_LENGTH,
  RECIPIENT_REQUIRED,
  exportError,
  exportFieldErrors,
  saveFile,
} from './exportMedicalRecord';

/** UC-53 Export Medical Record: choose the purpose and recipient, then download form 15/BV1 as a PDF. */
export function ExportMedicalRecordDialog({
  medicalRecordId,
  visitCode,
  onClose,
  api = medicalRecordApi,
  save = saveFile,
}) {
  const id = useId();
  const [purpose, setPurpose] = useState('');
  const [recipient, setRecipient] = useState('');
  const [errors, setErrors] = useState({});
  const [alert, setAlert] = useState('');
  const [busy, setBusy] = useState(false);
  const recipientRequired = RECIPIENT_REQUIRED.has(purpose);

  const submit = async (event) => {
    event.preventDefault();
    const fieldErrors = exportFieldErrors(purpose, recipient);
    setErrors(fieldErrors);
    setAlert('');
    if (Object.keys(fieldErrors).length > 0) return;
    setBusy(true);
    try {
      const file = await api.exportPdf(medicalRecordId, { purpose, recipient: recipient.trim() });
      save(file);
      toast.success(EXPORT_MSG.SUCCESS);
      onClose();
    } catch (error) {
      const mapped = exportError(error);
      if (mapped.field) setErrors({ [mapped.field]: mapped.message });
      else setAlert(mapped.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        noValidate
        onSubmit={submit}
        className="w-full max-w-md bg-white rounded-2xl shadow-xl overflow-hidden p-6 space-y-4"
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2">
            <Download className="w-5 h-5 text-[#127e7a]" />
            <h3 id={`${id}-title`} className="text-base font-bold text-slate-900">Xuất hồ sơ bệnh án (PDF)</h3>
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            disabled={busy}
            className="p-1 rounded-lg text-slate-400 hover:bg-slate-100 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <p className="text-xs text-slate-600">
          Bệnh án ngoại trú (mẫu 15/BV1) của lượt khám {visitCode}, lấy từ bản lưu trữ khi đóng hồ sơ.
        </p>

        {alert && <FormAlert>{alert}</FormAlert>}

        <Field id={`${id}-purpose`} label="Mục đích xuất" required error={errors.purpose}>
          <select
            id={`${id}-purpose`}
            value={purpose}
            onChange={(event) => setPurpose(event.target.value)}
            aria-invalid={errors.purpose ? true : undefined}
            aria-describedby={errors.purpose ? `${id}-purpose-error` : undefined}
            className={INPUT_CLASS}
          >
            <option value="">-- Chọn mục đích --</option>
            {EXPORT_PURPOSES.map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </Field>

        <Field
          id={`${id}-recipient`}
          label="Người nhận"
          required={recipientRequired}
          error={errors.recipient}
          hint="Họ tên / cơ quan nhận và số văn bản đề nghị (nếu có)"
        >
          <input
            id={`${id}-recipient`}
            type="text"
            value={recipient}
            maxLength={RECIPIENT_MAX_LENGTH + 50}
            onChange={(event) => setRecipient(event.target.value)}
            aria-invalid={errors.recipient ? true : undefined}
            aria-describedby={errors.recipient ? `${id}-recipient-error` : undefined}
            className={INPUT_CLASS}
          />
        </Field>

        <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
          >
            Hủy
          </button>
          <button
            type="submit"
            disabled={busy || !purpose}
            className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all cursor-pointer shadow-sm disabled:opacity-50"
          >
            {busy ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                <span>Đang xuất…</span>
              </>
            ) : (
              <>
                <Download className="w-3.5 h-3.5" />
                <span>Xuất PDF</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
