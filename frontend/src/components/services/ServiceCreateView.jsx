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
} from 'lucide-react';
import {
  SERVICE_TYPE_MAP,
  SERVICE_GROUP_LABELS,
  SERVICE_GROUP_TYPES,
} from '../../constants/serviceData';
import { INPUT_CLASS, FormField, FormSection, AccountPageHeader } from '../accounts/AccountFormParts';
import { EMPTY_SERVICE_FORM, validateServiceForm } from './serviceForm';
import { formatPrice } from './serviceFormat';

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

function PriceInput({ id, value, onChange, error, disabled }) {
  return (
    <div className="relative">
      <input
        id={id}
        type="text"
        inputMode="numeric"
        value={value === '' ? '' : formatPrice(Number(value))}
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
export function ServiceCreateView({ departments = [], onCreate, onCancel, onGoToList }) {
  const [form, setForm] = useState(EMPTY_SERVICE_FORM);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const errors = validateServiceForm(form);
  const shownErrors = touched ? errors : {};
  const has = (field) => Boolean(errors[field]);
  const examination = form.type === 'EXAMINATION';

  const groups = Object.entries(SERVICE_GROUP_LABELS).filter(([group]) => SERVICE_GROUP_TYPES[group] === form.type);
  const typeDepartments = departments.filter((d) => d.type === 'PARACLINICAL' && d.active !== false);

  const onChange = (field, value) => setForm((prev) => ({ ...prev, [field]: value }));

  // A new type keeps no group or department from the previous type.
  const onTypeChange = (type) => setForm((prev) => ({ ...prev, type, group: '', departmentId: '' }));

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await onCreate?.(form);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <AccountPageHeader
        title="Thêm dịch vụ"
        description="Thêm dịch vụ khám hoặc cận lâm sàng vào danh mục, kèm giá viện phí và giá BHYT"
        onBack={onCancel}
        crumbs={[{ label: 'Quản trị' }, { label: 'Dịch vụ và giá', onClick: onGoToList }, { label: 'Thêm dịch vụ' }]}
      />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_300px] gap-5 items-start">
        <div className="space-y-5">
          <FormSection icon={FileText} title="Thông tin dịch vụ" description="Mã, tên và loại dịch vụ">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FormField id="service-code" label="Mã dịch vụ" required error={shownErrors.code} hint="Tối đa 20 ký tự, ví dụ XN-SH-001">
                <input
                  id="service-code"
                  type="text"
                  value={form.code}
                  onChange={(e) => onChange('code', e.target.value.toUpperCase())}
                  placeholder="VD: XN-SH-006"
                  maxLength={20}
                  aria-invalid={Boolean(shownErrors.code)}
                  className={`${INPUT_CLASS} font-mono`}
                />
              </FormField>

              <FormField id="service-type" label="Loại dịch vụ" required>
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
              </FormField>

              <FormField id="service-name" label="Tên dịch vụ" required error={shownErrors.name} className="md:col-span-2">
                <input
                  id="service-name"
                  type="text"
                  value={form.name}
                  onChange={(e) => onChange('name', e.target.value)}
                  placeholder="VD: Định lượng Glucose máu"
                  maxLength={200}
                  aria-invalid={Boolean(shownErrors.name)}
                  className={INPUT_CLASS}
                />
              </FormField>

              <FormField id="service-description" label="Mô tả" className="md:col-span-2">
                <textarea
                  id="service-description"
                  rows={3}
                  value={form.description}
                  onChange={(e) => onChange('description', e.target.value)}
                  placeholder="Ghi chú thêm về dịch vụ (không bắt buộc)"
                  className={`${INPUT_CLASS} resize-none`}
                />
              </FormField>
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
                <FormField
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
                </FormField>

                <FormField id="service-group" label="Nhóm dịch vụ" required error={shownErrors.group}>
                  <select
                    id="service-group"
                    value={form.group}
                    onChange={(e) => onChange('group', e.target.value)}
                    aria-invalid={Boolean(shownErrors.group)}
                    className={`${INPUT_CLASS} cursor-pointer`}
                  >
                    <option value="">Chọn nhóm</option>
                    {groups.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </FormField>
              </div>
            )}
          </FormSection>

          <FormSection icon={Banknote} title="Giá dịch vụ" description="Mỗi dịch vụ có giá viện phí và giá BHYT">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FormField id="service-clinic-price" label="Giá viện phí" required error={shownErrors.clinicPrice} hint="Lớn hơn 0, tối đa 100.000.000 đ">
                <PriceInput
                  id="service-clinic-price"
                  value={form.clinicPrice}
                  onChange={(value) => onChange('clinicPrice', value)}
                  error={shownErrors.clinicPrice}
                />
              </FormField>

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
                  <p id="service-insurance-price-error" className="text-xs text-rose-600">{shownErrors.insurancePrice}</p>
                ) : (
                  <p className="text-xs text-slate-400">
                    {form.insuranceCovered ? 'Từ 0 đến 100.000.000 đ' : 'Không áp dụng — bệnh nhân BHYT trả theo giá viện phí'}
                  </p>
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
                detail="Mã tối đa 20 ký tự, không trùng dịch vụ khác"
              />
              <ChecklistItem
                done={!has('departmentId') && !has('group')}
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
            {touched && Object.keys(errors).length > 0 && (
              <p role="alert" className="text-xs text-rose-600 text-center">Vui lòng sửa các ô được đánh dấu.</p>
            )}
          </div>
        </aside>
      </div>
    </form>
  );
}

export default ServiceCreateView;
