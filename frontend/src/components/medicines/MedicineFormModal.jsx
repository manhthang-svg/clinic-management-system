import { useState } from 'react';
import { Pencil, Pill } from 'lucide-react';
import { ConfirmModal } from '../ui';
import { INPUT_CLASS, Field, ServiceFormModal } from '../services/ServiceFormParts';
import { MSG38 } from '../services/serviceMessages';
import { MEDICINE_CONTROL_GROUP_LABELS } from '../../constants/medicineData';
import { EMPTY_MEDICINE_FORM, FIELD_LABELS, LIMITS, medicineToForm, validateMedicineForm } from './medicineForm';
import { medicineSaveErrors } from './medicineErrors';

const NO_SERVER_ERRORS = { fields: {}, message: null };
const SELECT_CLASS = `${INPUT_CLASS} cursor-pointer`;

function TextField({ name, form, errors, update, hint, disabled = false, required = true, transform }) {
  const id = `medicine-${name}`;
  return (
    <Field id={id} label={FIELD_LABELS[name]} required={required} error={errors[name]} hint={hint}>
      <input
        id={id}
        type="text"
        value={form[name]}
        onChange={(event) => update({ [name]: transform ? transform(event.target.value) : event.target.value })}
        maxLength={LIMITS[name]}
        disabled={disabled}
        aria-invalid={Boolean(errors[name])}
        aria-describedby={errors[name] ? `${id}-error` : undefined}
        className={INPUT_CLASS}
      />
    </Field>
  );
}

/**
 * UC-82 Create Medicine (no medicine) and UC-83 Update Medicine. onSubmit receives the form and may throw an
 * ApiError, which stays in the dialog.
 */
export function MedicineFormModal({ medicine = null, routes = [], units = [], onSubmit, onClose }) {
  const editing = medicine !== null;
  const initialForm = editing ? medicineToForm(medicine) : EMPTY_MEDICINE_FORM;
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverErrors, setServerErrors] = useState(NO_SERVER_ERRORS);
  const [confirmingClose, setConfirmingClose] = useState(false);

  const errors = validateMedicineForm(form, { editing });
  const shownErrors = touched ? { ...serverErrors.fields, ...errors } : {};
  const unchanged = JSON.stringify(form) === JSON.stringify(initialForm);
  const alert = touched && Object.keys(errors).length > 0 ? 'Vui lòng sửa các ô được đánh dấu.' : serverErrors.message;

  const update = (changes) => {
    setForm((previous) => ({ ...previous, ...changes }));
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
      setServerErrors(medicineSaveErrors(error));
      setSubmitting(false);
    }
  };

  // Leaving with unsaved changes asks first (MSG38).
  const requestClose = () => {
    if (unchanged) onClose();
    else setConfirmingClose(true);
  };

  const fieldProps = { form, errors: shownErrors, update };

  return (
    <>
      <ServiceFormModal
        id="medicine-form-modal"
        icon={editing ? Pencil : Pill}
        title={editing ? 'Sửa thông tin thuốc' : 'Thêm thuốc'}
        description={editing ? `${medicine.code} · Mã thuốc không thay đổi được` : 'Thuốc mới ở trạng thái đang hoạt động'}
        submitLabel={editing ? 'Lưu thay đổi' : 'Thêm thuốc'}
        submitting={submitting}
        submitDisabled={editing && unchanged}
        alert={alert}
        onSubmit={submit}
        onClose={requestClose}
        maxWidthClass="max-w-2xl"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <TextField
            name="code"
            {...fieldProps}
            disabled={editing}
            transform={(value) => value.toUpperCase()}
            hint={editing ? undefined : 'Mã nội bộ: chữ in hoa, chữ số hoặc dấu gạch ngang'}
          />
          <TextField name="registrationNumber" {...fieldProps} hint="Do Cục Quản lý Dược cấp" />
          <TextField name="name" {...fieldProps} />
          <TextField name="activeIngredient" {...fieldProps} />
          <TextField name="strength" {...fieldProps} hint="Ví dụ: 500mg" />

          <Field id="medicine-route" label={FIELD_LABELS.route} required error={shownErrors.route}>
            <select
              id="medicine-route"
              value={form.route}
              onChange={(event) => update({ route: event.target.value })}
              aria-invalid={Boolean(shownErrors.route)}
              aria-describedby={shownErrors.route ? 'medicine-route-error' : undefined}
              className={SELECT_CLASS}
            >
              <option value="">Chọn đường dùng</option>
              {routes.map((route) => (
                <option key={route} value={route}>{route}</option>
              ))}
            </select>
          </Field>

          <Field id="medicine-unit" label={FIELD_LABELS.unit} required error={shownErrors.unit} hint="Ví dụ: Viên, Ống, Lọ">
            <input
              id="medicine-unit"
              type="text"
              list="medicine-unit-options"
              value={form.unit}
              onChange={(event) => update({ unit: event.target.value })}
              maxLength={LIMITS.unit}
              aria-invalid={Boolean(shownErrors.unit)}
              aria-describedby={shownErrors.unit ? 'medicine-unit-error' : undefined}
              className={INPUT_CLASS}
            />
            <datalist id="medicine-unit-options">
              {units.map((unit) => <option key={unit} value={unit} />)}
            </datalist>
          </Field>

          <Field
            id="medicine-control-group"
            label={FIELD_LABELS.controlGroup}
            required
            error={shownErrors.controlGroup}
            hint="Thuốc khác nhóm Thông thường được lưu nhưng không kê được"
          >
            <select
              id="medicine-control-group"
              value={form.controlGroup}
              onChange={(event) => update({ controlGroup: event.target.value })}
              aria-invalid={Boolean(shownErrors.controlGroup)}
              aria-describedby={shownErrors.controlGroup ? 'medicine-control-group-error' : undefined}
              className={SELECT_CLASS}
            >
              <option value="">Chọn nhóm kiểm soát</option>
              {Object.entries(MEDICINE_CONTROL_GROUP_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </Field>

          <TextField name="insuranceCode" {...fieldProps} required={false} hint="Không bắt buộc" />
        </div>
      </ServiceFormModal>
      <ConfirmModal
        isOpen={confirmingClose}
        description={MSG38}
        cancelText="Tiếp tục chỉnh sửa"
        confirmText="Loại bỏ"
        variant="destructive"
        onClose={() => setConfirmingClose(false)}
        onConfirm={() => {
          setConfirmingClose(false);
          onClose();
        }}
      />
    </>
  );
}

export default MedicineFormModal;
