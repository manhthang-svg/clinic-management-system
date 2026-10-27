import { useState } from 'react';
import {
  FileText,
  Building2,
  Banknote,
  ClipboardCheck,
  CheckCircle2,
  Circle,
  Info,
  Plus,
  X,
  AlertCircle,
} from 'lucide-react';
import { SERVICE_TYPE_MAP } from '../../constants/serviceData';
import { INPUT_CLASS as BASE_INPUT_CLASS, FormSection, AccountPageHeader } from '../accounts/AccountFormParts';
import {
  EMPTY_SERVICE_FORM,
  MAX_CODE_LENGTH,
  MAX_NAME_LENGTH,
  validateServiceForm,
} from './serviceForm';
import { serviceCreateErrors } from './serviceErrors';
import { formatPrice } from './serviceFormat';

// Invalid fields get a tinted background and a rose focus ring instead of the teal one.
const INPUT_CLASS =
  `${BASE_INPUT_CLASS} aria-invalid:bg-rose-50/60 aria-invalid:focus:border-rose-400 aria-invalid:focus:ring-rose-500/15`;

function FieldError({ id, children }) {
  return (
    <p id={id} className="flex items-start gap-1.5 text-xs font-medium text-rose-600">
      <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" />
      <span>{children}</span>
    </p>
  );
}

function FieldHint({ children }) {
  return <p className="text-xs text-slate-400">{children}</p>;
}

function Field({ id, label, required, error, hint, className = '', children }) {
  return (
    <div className={`space-y-1.5 ${className}`}>
      <label htmlFor={id} className="block text-sm font-semibold text-slate-700">
        {label}
        {required && <span className="text-rose-500 ml-0.5">*</span>}
      </label>
      {children}
      {error ? <FieldError id={`${id}-error`}>{error}</FieldError> : hint && <FieldHint>{hint}</FieldHint>}
    </div>
  );
}

function ChecklistItem({ done, title, detail }) {
  const Icon = done ? CheckCircle2 : Circle;
  return (
    <li className="flex items-start gap-3">
      <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${done ? 'text-emerald-500' : 'text-slate-300'}`} />
      <div>
        <div className="text-sm font-semibold text-slate-800">{title}</div>
        <div className="text-xs text-slate-500">{detail}</div>
      </div>
    </li>
  );
}

// While focused the field shows the raw digits: reformatting under the caret (1000 -> 1.000)
// desyncs Vietnamese IMEs such as Unikey, which then retype their buffer (1000 -> 1100).
function PriceInput({ id, value, onChange, error, disabled }) {
  const [focused, setFocused] = useState(false);
  const shown = value === '' || focused ? value : formatPrice(Number(value));

  return (
    <div className="relative">
      <input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={shown}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 10))}
        placeholder="0"
        disabled={disabled}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`${INPUT_CLASS} pr-14 text-right`}
      />
      <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400 pointer-events-none">VNĐ</span>
    </div>
  );
}

/** Create Service (UC-20 → UC-23 service management, BR-08). */
export function ServiceCreateView({ groups = [], departments = [], onCreate, onCancel, onGoToList }) {
  const [form, setForm] = useState(EMPTY_SERVICE_FORM);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverErrors, setServerErrors] = useState({ fields: {}, message: null });

  const errors = validateServiceForm(form);
  // Client checks win; server errors cover what only the backend knows, such as a duplicate code.
  const shownErrors = touched ? { ...serverErrors.fields, ...errors } : {};
  const has = (field) => Boolean(errors[field] || serverErrors.fields[field]);
  const examination = form.type === 'EXAMINATION';

  const typeGroups = groups.filter((group) => group.type === form.type);
  const typeDepartments = departments.filter((d) => d.type === 'PARACLINICAL' && d.active !== false);

  const clearServerError = (field) =>
    setServerErrors((prev) => {
      if (!(field in prev.fields)) return prev;
      const fields = { ...prev.fields };
      delete fields[field];
      return { fields, message: Object.keys(fields).length > 0 ? prev.message : null };
    });

  const onChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    clearServerError(field);
  };

  // A new type keeps no group or department from the previous type.
  const onTypeChange = (type) => {
    setForm((prev) => ({ ...prev, type, serviceGroupId: '', departmentId: '' }));
    setServerErrors({ fields: {}, message: null });
  };

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    setServerErrors({ fields: {}, message: null });
    try {
      await onCreate(form);
    } catch (error) {
      setServerErrors(serviceCreateErrors(error));
    } finally {
      setSubmitting(false);
    }
  };

  const alertMessage =
    touched && Object.keys(errors).length > 0 ? 'Vui lòng sửa các ô được đánh dấu.' : serverErrors.message;

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <AccountPageHeader
        title="Thêm dịch vụ"
        description="Thêm dịch vụ khám hoặc cận lâm sàng vào danh mục, kèm giá viện phí và giá BHYT"
        crumbs={[{ label: 'Quản lý dịch vụ và giá', onClick: onGoToList }, { label: 'Thêm dịch vụ' }]}
      />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_300px] gap-5 items-start">
        <div className="space-y-5">
          <FormSection icon={FileText} title="Thông tin dịch vụ" description="Mã, tên và loại dịch vụ">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <Field id="service-code" label="Mã dịch vụ" required error={shownErrors.code} hint={`Tối đa ${MAX_CODE_LENGTH} ký tự, ví dụ XN-SH-001`}>
                <input
                  id="service-code"
                  type="text"
                  value={form.code}
                  onChange={(e) => onChange('code', e.target.value.toUpperCase())}
                  placeholder="VD: XN-SH-006"
                  maxLength={MAX_CODE_LENGTH}
                  aria-invalid={Boolean(shownErrors.code)}
                  className={`${INPUT_CLASS} font-mono`}
                />
              </Field>

              <Field id="service-type" label="Loại dịch vụ" required>
                <select
                  id="service-type"
                  value={form.type}
                  onChange={(e) => onTypeChange(e.target.value)}
                  className={`${INPUT_CLASS} cursor-pointer`}
                >
                  {Object.entries(SERVICE_TYPE_MAP).map(([value, type]) => (
                    <option key={value} value={value}>{type.label}</option>
                  ))}
                </select>
              </Field>

              <Field id="service-name" label="Tên dịch vụ" required error={shownErrors.name} className="md:col-span-2">
                <input
                  id="service-name"
                  type="text"
                  value={form.name}
                  onChange={(e) => onChange('name', e.target.value)}
                  placeholder="VD: Định lượng Glucose máu"
                  maxLength={MAX_NAME_LENGTH}
                  aria-invalid={Boolean(shownErrors.name)}
                  className={INPUT_CLASS}
                />
              </Field>

              <Field id="service-description" label="Mô tả" className="md:col-span-2">
                <textarea
                  id="service-description"
                  rows={3}
                  value={form.description}
                  onChange={(e) => onChange('description', e.target.value)}
                  placeholder="Ghi chú thêm về dịch vụ (không bắt buộc)"
                  className={`${INPUT_CLASS} resize-none`}
                />
              </Field>
            </div>
          </FormSection>

          <FormSection icon={Building2} title="Nơi thực hiện" description="Khoa và nhóm dịch vụ thực hiện chỉ định">
            {examination ? (
              <p className="flex items-start gap-2 p-3.5 rounded-xl bg-slate-50 border border-slate-100 text-sm text-slate-600">
                <Info className="w-4 h-4 shrink-0 mt-0.5 text-[#127e7a]" />
                Dịch vụ khám bệnh dùng chung cho các phòng khám, không gắn với khoa hay nhóm dịch vụ. Chỉ có một dịch vụ khám bệnh đang hoạt động.
              </p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <Field
                  id="service-department"
                  label="Khoa thực hiện"
                  required
                  error={shownErrors.departmentId}
                  hint={typeDepartments.length === 0 ? 'Chưa có dữ liệu khoa.' : undefined}
                >
                  <select
                    id="service-department"
                    value={form.departmentId}
                    onChange={(e) => onChange('departmentId', e.target.value)}
                    aria-invalid={Boolean(shownErrors.departmentId)}
                    className={`${INPUT_CLASS} cursor-pointer`}
                  >
                    <option value="">Chọn khoa</option>
                    {typeDepartments.map((d) => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </Field>

                <Field
                  id="service-group"
                  label="Nhóm dịch vụ"
                  required
                  error={shownErrors.serviceGroupId}
                  hint={typeGroups.length === 0 ? 'Chưa có nhóm dịch vụ cho loại này.' : undefined}
                >
                  <select
                    id="service-group"
                    value={form.serviceGroupId}
                    onChange={(e) => onChange('serviceGroupId', e.target.value)}
                    aria-invalid={Boolean(shownErrors.serviceGroupId)}
                    className={`${INPUT_CLASS} cursor-pointer`}
                  >
                    <option value="">Chọn nhóm</option>
                    {typeGroups.map((group) => (
                      <option key={group.id} value={group.id}>{group.name}</option>
                    ))}
                  </select>
                </Field>
              </div>
            )}
          </FormSection>

          <FormSection icon={Banknote} title="Giá dịch vụ" description="Mỗi dịch vụ có giá viện phí và giá BHYT">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <Field id="service-clinic-price" label="Giá viện phí" required error={shownErrors.clinicPrice} hint="Lớn hơn 0, tối đa 100.000.000 đ">
                <PriceInput
                  id="service-clinic-price"
                  value={form.clinicPrice}
                  onChange={(value) => onChange('clinicPrice', value)}
                  error={shownErrors.clinicPrice}
                />
              </Field>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between gap-3">
                  <label htmlFor="service-insurance-price" className="block text-sm font-semibold text-slate-700">
                    Giá BHYT{form.insuranceCovered && <span className="text-rose-500 ml-0.5">*</span>}
                  </label>
                  <label className="inline-flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={form.insuranceCovered}
                      onChange={(e) => setForm((prev) => ({ ...prev, insuranceCovered: e.target.checked, insurancePrice: '' }))}
                      className="w-4 h-4 rounded border-slate-300 accent-[#127e7a] cursor-pointer"
                    />
                    Thuộc phạm vi BHYT
                  </label>
                </div>
                <PriceInput
                  id="service-insurance-price"
                  value={form.insurancePrice}
                  onChange={(value) => onChange('insurancePrice', value)}
                  error={shownErrors.insurancePrice}
                  disabled={!form.insuranceCovered}
                />
                {shownErrors.insurancePrice ? (
                  <FieldError id="service-insurance-price-error">{shownErrors.insurancePrice}</FieldError>
                ) : (
                  <FieldHint>
                    {form.insuranceCovered ? 'Lớn hơn 0, tối đa 100.000.000 đ' : 'Không áp dụng — bệnh nhân BHYT trả theo giá viện phí'}
                  </FieldHint>
                )}
              </div>
            </div>
          </FormSection>
        </div>

        <aside className="space-y-5 xl:sticky xl:top-4">
          <FormSection icon={ClipboardCheck} title="Kiểm tra trước khi lưu" description="Hoàn thành các mục dưới đây">
            <ul className="space-y-4">
              <ChecklistItem
                done={!has('code') && !has('name')}
                title="Mã và tên dịch vụ hợp lệ"
                detail={`Mã tối đa ${MAX_CODE_LENGTH} ký tự, không trùng dịch vụ khác`}
              />
              <ChecklistItem
                done={!has('departmentId') && !has('serviceGroupId')}
                title="Đã chọn nơi thực hiện"
                detail={examination ? 'Khám bệnh không cần khoa và nhóm' : 'Khoa và nhóm dịch vụ'}
              />
              <ChecklistItem
                done={!has('clinicPrice') && !has('insurancePrice')}
                title="Đã nhập giá"
                detail="Giá viện phí và giá BHYT (nếu có)"
              />
              <li className="flex items-start gap-3">
                <Info className="w-5 h-5 shrink-0 mt-0.5 text-[#3b7be0]" />
                <div>
                  <div className="text-sm font-semibold text-slate-800">Trạng thái ban đầu</div>
                  <div className="text-xs text-slate-500">Dịch vụ mới ở trạng thái Đang hoạt động và hiển thị khi chỉ định</div>
                </div>
              </li>
            </ul>
          </FormSection>

          <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-2.5">
            <button
              type="submit"
              disabled={submitting}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <Plus className="w-4 h-4" />
              {submitting ? 'Đang lưu…' : 'Thêm dịch vụ'}
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
            >
              <X className="w-4 h-4" />
              Hủy
            </button>
            {alertMessage && (
              <div role="alert" className="flex items-start gap-2.5 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <p className="text-xs font-medium leading-5">{alertMessage}</p>
              </div>
            )}
          </div>
        </aside>
      </div>
    </form>
  );
}

export default ServiceCreateView;
