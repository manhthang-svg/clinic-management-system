import { useState } from 'react';
import { Banknote, Pencil } from 'lucide-react';
import {
  MAX_NAME_LENGTH,
  serviceToInfoForm,
  serviceToPriceForm,
  validateServiceInfoForm,
  validateServicePriceForm,
} from './serviceForm';
import { serviceUpdateErrors } from './serviceErrors';
import { INPUT_CLASS, Field, FieldError, FieldHint, PriceInput, ServiceFormModal } from './ServiceFormParts';

const NO_SERVER_ERRORS = { fields: {}, message: null };

// Shared submit flow: client validation first, then the server call; errors stay in the dialog.
function useServiceUpdateForm(initialForm, validate, onSubmit) {
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverErrors, setServerErrors] = useState(NO_SERVER_ERRORS);

  const errors = validate(form);
  const shownErrors = touched ? { ...serverErrors.fields, ...errors } : {};
  const unchanged = JSON.stringify(form) === JSON.stringify(initialForm);
  const alert = touched && Object.keys(errors).length > 0 ? 'Vui lòng sửa các ô được đánh dấu.' : serverErrors.message;

  const update = (changes) => {
    setForm((prev) => ({ ...prev, ...changes }));
    setServerErrors(NO_SERVER_ERRORS);
  };

  const submit = async (event) => {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    setServerErrors(NO_SERVER_ERRORS);
    try {
      await onSubmit(form);
    } catch (error) {
      setServerErrors(serviceUpdateErrors(error));
      setSubmitting(false);
    }
  };

  return { form, update, shownErrors, unchanged, alert, submitting, submit };
}

/** UC-23 Update service information. */
export function ServiceInfoModal({ service, onSubmit, onClose }) {
  const { form, update, shownErrors, unchanged, alert, submitting, submit } =
    useServiceUpdateForm(serviceToInfoForm(service), validateServiceInfoForm, onSubmit);

  return (
    <ServiceFormModal
      id="service-info-modal"
      icon={Pencil}
      title="Sửa thông tin dịch vụ"
      description={`${service.code} · Mã, loại và nơi thực hiện không thay đổi được`}
      submitLabel="Lưu thay đổi"
      submitting={submitting}
      submitDisabled={unchanged}
      alert={alert}
      onSubmit={submit}
      onClose={onClose}
    >
      <Field id="edit-service-name" label="Tên dịch vụ" required error={shownErrors.name}>
        <input
          id="edit-service-name"
          type="text"
          value={form.name}
          onChange={(e) => update({ name: e.target.value })}
          maxLength={MAX_NAME_LENGTH}
          aria-invalid={Boolean(shownErrors.name)}
          aria-describedby={shownErrors.name ? 'edit-service-name-error' : undefined}
          className={INPUT_CLASS}
        />
      </Field>

      <Field id="edit-service-description" label="Mô tả" error={shownErrors.description}>
        <textarea
          id="edit-service-description"
          rows={4}
          value={form.description}
          onChange={(e) => update({ description: e.target.value })}
          placeholder="Ghi chú thêm về dịch vụ (không bắt buộc)"
          aria-invalid={Boolean(shownErrors.description)}
          className={`${INPUT_CLASS} resize-none`}
        />
      </Field>
    </ServiceFormModal>
  );
}

/** UC-24 Update service prices (BR-08, BR-09). */
export function ServicePriceModal({ service, onSubmit, onClose }) {
  const { form, update, shownErrors, unchanged, alert, submitting, submit } =
    useServiceUpdateForm(serviceToPriceForm(service), validateServicePriceForm, onSubmit);

  return (
    <ServiceFormModal
      id="service-price-modal"
      icon={Banknote}
      title="Cập nhật giá dịch vụ"
      description={`${service.code} · ${service.name}`}
      submitLabel="Cập nhật giá"
      submitting={submitting}
      submitDisabled={unchanged}
      alert={alert}
      onSubmit={submit}
      onClose={onClose}
    >
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <Field id="edit-clinic-price" label="Giá viện phí" required error={shownErrors.clinicPrice} hint="Lớn hơn 0, tối đa 100.000.000 đ">
          <PriceInput
            id="edit-clinic-price"
            value={form.clinicPrice}
            onChange={(value) => update({ clinicPrice: value })}
            error={shownErrors.clinicPrice}
          />
        </Field>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-3">
            <label htmlFor="edit-insurance-price" className="block text-sm font-semibold text-slate-700">
              Giá BHYT{form.insuranceCovered && <span className="text-rose-500 ml-0.5">*</span>}
            </label>
            <label className="inline-flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
              <input
                type="checkbox"
                checked={form.insuranceCovered}
                onChange={(e) => update({ insuranceCovered: e.target.checked, insurancePrice: '' })}
                className="w-4 h-4 rounded border-slate-300 accent-[#127e7a] cursor-pointer"
              />
              Thuộc phạm vi BHYT
            </label>
          </div>
          <PriceInput
            id="edit-insurance-price"
            value={form.insurancePrice}
            onChange={(value) => update({ insurancePrice: value })}
            error={shownErrors.insurancePrice}
            disabled={!form.insuranceCovered}
          />
          {shownErrors.insurancePrice ? (
            <FieldError id="edit-insurance-price-error">{shownErrors.insurancePrice}</FieldError>
          ) : (
            <FieldHint>
              {form.insuranceCovered ? 'Lớn hơn 0, tối đa 100.000.000 đ' : 'Không áp dụng — bệnh nhân BHYT trả theo giá viện phí'}
            </FieldHint>
          )}
        </div>
      </div>

      <p className="text-xs text-slate-500 p-3 rounded-xl bg-slate-50 border border-slate-100">
        Giá mới chỉ áp dụng cho các chỉ định tạo sau khi cập nhật. Các chỉ định đã tạo giữ nguyên giá cũ.
      </p>
    </ServiceFormModal>
  );
}
