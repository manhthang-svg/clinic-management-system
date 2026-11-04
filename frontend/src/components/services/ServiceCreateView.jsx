import { useState } from 'react';
import { Banknote, FileText, FlaskConical, Info, Plus, X } from 'lucide-react';
import {
  SERVICE_TYPE_MAP,
  resolveServiceGroupType,
  doesServiceGroupMatchDepartment,
} from '../../constants/serviceData';
import { FormSection, AccountPageHeader } from '../accounts/AccountFormParts';
import {
  EMPTY_SERVICE_FORM,
  MAX_CODE_LENGTH,
  MAX_NAME_LENGTH,
  MAX_DESCRIPTION_LENGTH,
  validateServiceForm,
} from './serviceForm';
import { serviceCreateErrors } from './serviceErrors';
import { INPUT_CLASS, Field, FieldError, FieldHint, FormAlert, PriceInput } from './ServiceFormParts';
import { LabParameterSelector } from './LabParameterSelector';
import { LabReferenceRangeModal } from './LabReferenceRangeModal';

/** Create Service (UC-22). */
export function ServiceCreateView({
  groups = [],
  departments = [],
  labParameters = [],
  referenceRangeApi,
  onReferenceRangesChanged,
  onCreate,
  onCancel,
  onGoToList,
}) {
  const [form, setForm] = useState(EMPTY_SERVICE_FORM);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverErrors, setServerErrors] = useState({ fields: {}, message: null });
  const [referenceParameter, setReferenceParameter] = useState(null);

  const errors = validateServiceForm(form);
  const shownErrors = touched ? { ...serverErrors.fields, ...errors } : {};
  const examination = form.type === 'EXAMINATION';
  const selectedDept = departments.find((department) => String(department.id) === String(form.departmentId));
  const typeDepartments = departments.filter(
    (department) => department.type === 'PARACLINICAL' && department.active !== false,
  );
  const typeGroups = groups.filter((group) => {
    if (resolveServiceGroupType(group) !== form.type && group.type !== form.type) return false;
    return selectedDept ? doesServiceGroupMatchDepartment(group, selectedDept) : true;
  });

  const clearServerError = (field) =>
    setServerErrors((previous) => {
      if (!(field in previous.fields)) return previous;
      const fields = { ...previous.fields };
      delete fields[field];
      return { fields, message: Object.keys(fields).length > 0 ? previous.message : null };
    });

  const onChange = (field, value) => {
    setForm((previous) => {
      const next = { ...previous, [field]: value };
      if (field === 'departmentId') {
        const nextDepartment = departments.find((department) => String(department.id) === String(value));
        const currentGroup = groups.find((group) => String(group.id) === String(previous.serviceGroupId));
        if (currentGroup && nextDepartment && !doesServiceGroupMatchDepartment(currentGroup, nextDepartment)) {
          next.serviceGroupId = '';
        }
      }
      if (field === 'serviceGroupId') {
        const nextGroup = groups.find((group) => String(group.id) === String(value));
        if (nextGroup?.departmentId) next.departmentId = String(nextGroup.departmentId);
      }
      return next;
    });
    clearServerError(field);
  };

  const onTypeChange = (type) => {
    setForm((previous) => ({
      ...previous,
      type,
      serviceGroupId: '',
      departmentId: '',
      labParameterIds: [],
    }));
    setServerErrors({ fields: {}, message: null });
  };

  const setInsuranceExcluded = (excluded) => {
    setForm((previous) => ({
      ...previous,
      insuranceCovered: !excluded,
      insurancePrice: excluded ? '' : previous.insurancePrice,
    }));
    clearServerError('insurancePrice');
  };

  const submit = async (event) => {
    event.preventDefault();
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
    touched && Object.keys(errors).length > 0
      ? 'Vui lòng sửa các trường được đánh dấu trước khi lưu.'
      : serverErrors.message;

  return (
    <>
      <form onSubmit={submit} noValidate className="space-y-5">
      <AccountPageHeader
        title="Thêm dịch vụ"
        description="Tạo dịch vụ khám hoặc cận lâm sàng mới kèm giá và, với xét nghiệm, các chỉ số."
        crumbs={[{ label: 'Quản lý dịch vụ và giá', onClick: onGoToList }, { label: 'Thêm dịch vụ' }]}
      />

      <FormSection
        icon={FileText}
        title="Thông tin dịch vụ"
        description="Nhập thông tin nhận diện và nơi thực hiện dịch vụ"
      >
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
          <Field
            id="service-code"
            label="Mã dịch vụ"
            required
            error={shownErrors.code}
            hint={`Tối đa ${MAX_CODE_LENGTH} ký tự, ví dụ XN-HH-001`}
            className="lg:col-span-4"
          >
            <input
              id="service-code"
              type="text"
              value={form.code}
              onChange={(event) => onChange('code', event.target.value.toUpperCase())}
              placeholder="VD: XN-HH-001"
              maxLength={MAX_CODE_LENGTH}
              aria-invalid={Boolean(shownErrors.code)}
              aria-describedby={shownErrors.code ? 'service-code-error' : undefined}
              className={`${INPUT_CLASS} font-mono`}
            />
          </Field>

          <Field
            id="service-name"
            label="Tên dịch vụ"
            required
            error={shownErrors.name}
            className="lg:col-span-8"
          >
            <input
              id="service-name"
              type="text"
              value={form.name}
              onChange={(event) => onChange('name', event.target.value)}
              placeholder="VD: Tổng phân tích tế bào máu ngoại vi"
              maxLength={MAX_NAME_LENGTH}
              aria-invalid={Boolean(shownErrors.name)}
              aria-describedby={shownErrors.name ? 'service-name-error' : undefined}
              className={INPUT_CLASS}
            />
          </Field>

          <Field id="service-type" label="Loại dịch vụ" required className="lg:col-span-4">
            <select
              id="service-type"
              value={form.type}
              onChange={(event) => onTypeChange(event.target.value)}
              className={`${INPUT_CLASS} cursor-pointer`}
            >
              {Object.entries(SERVICE_TYPE_MAP).map(([value, type]) => (
                <option key={value} value={value}>{type.label}</option>
              ))}
            </select>
          </Field>

          {!examination && (
            <>
              <Field
                id="service-group"
                label="Nhóm dịch vụ"
                required
                error={shownErrors.serviceGroupId}
                hint={typeGroups.length === 0 ? 'Chưa có nhóm phù hợp với loại dịch vụ.' : undefined}
                className="lg:col-span-4"
              >
                <select
                  id="service-group"
                  value={form.serviceGroupId}
                  onChange={(event) => onChange('serviceGroupId', event.target.value)}
                  aria-invalid={Boolean(shownErrors.serviceGroupId)}
                  aria-describedby={shownErrors.serviceGroupId ? 'service-group-error' : undefined}
                  className={`${INPUT_CLASS} cursor-pointer`}
                >
                  <option value="">Chọn nhóm dịch vụ</option>
                  {typeGroups.map((group) => (
                    <option key={group.id} value={group.id}>{group.name}</option>
                  ))}
                </select>
              </Field>

              <Field
                id="service-department"
                label="Khoa thực hiện"
                required
                error={shownErrors.departmentId}
                hint={typeDepartments.length === 0 ? 'Chưa có dữ liệu khoa cận lâm sàng.' : undefined}
                className="lg:col-span-4"
              >
                <select
                  id="service-department"
                  value={form.departmentId}
                  onChange={(event) => onChange('departmentId', event.target.value)}
                  aria-invalid={Boolean(shownErrors.departmentId)}
                  aria-describedby={shownErrors.departmentId ? 'service-department-error' : undefined}
                  className={`${INPUT_CLASS} cursor-pointer`}
                >
                  <option value="">Chọn khoa thực hiện</option>
                  {typeDepartments.map((department) => (
                    <option key={department.id} value={department.id}>{department.name}</option>
                  ))}
                </select>
              </Field>
            </>
          )}

          {examination && (
            <div className="flex items-start gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3.5 text-sm text-slate-600 lg:col-span-8">
              <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-[#127e7a]" />
              Dịch vụ khám bệnh dùng chung cho các phòng khám, không gắn với khoa hoặc nhóm dịch vụ.
            </div>
          )}

          <Field
            id="service-description"
            label="Mô tả"
            error={shownErrors.description}
            className="lg:col-span-12"
          >
            <textarea
              id="service-description"
              rows={3}
              value={form.description}
              onChange={(event) => onChange('description', event.target.value)}
              placeholder="Ghi chú thêm về dịch vụ (không bắt buộc)"
              maxLength={MAX_DESCRIPTION_LENGTH}
              aria-invalid={Boolean(shownErrors.description)}
              aria-describedby={shownErrors.description ? 'service-description-error' : undefined}
              className={`${INPUT_CLASS} resize-y`}
            />
          </Field>
        </div>
      </FormSection>

      <FormSection
        icon={Banknote}
        title="Giá dịch vụ"
        description="Khai báo giá viện phí và giá áp dụng cho bảo hiểm y tế"
      >
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
          <Field
            id="service-clinic-price"
            label="Giá viện phí (VNĐ)"
            required
            error={shownErrors.clinicPrice}
            hint="Lớn hơn 0, tối đa 100.000.000 đ"
          >
            <PriceInput
              id="service-clinic-price"
              value={form.clinicPrice}
              onChange={(value) => onChange('clinicPrice', value)}
              error={shownErrors.clinicPrice}
            />
          </Field>

          <div className="space-y-2">
            <div className="flex min-h-6 items-center justify-between gap-4">
              <label htmlFor="service-insurance-price" className="text-sm font-semibold text-slate-700">
                Giá BHYT (VNĐ){form.insuranceCovered && <span className="ml-0.5 text-rose-500">*</span>}
              </label>
              <label className="inline-flex cursor-pointer items-center gap-2 text-sm text-slate-600">
                <input
                  type="checkbox"
                  checked={!form.insuranceCovered}
                  onChange={(event) => setInsuranceExcluded(event.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-300 accent-[#127e7a]"
                />
                Không thuộc BHYT
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
                {form.insuranceCovered
                  ? 'Lớn hơn 0, tối đa 100.000.000 đ'
                  : 'Dịch vụ không áp dụng giá bảo hiểm y tế.'}
              </FieldHint>
            )}
          </div>
        </div>
      </FormSection>

      {form.type === 'LAB' && (
        <FormSection
          icon={FlaskConical}
          title="Chỉ số xét nghiệm"
          description="Chọn và sắp xếp các chỉ số thuộc dịch vụ xét nghiệm"
        >
          <LabParameterSelector
            parameters={labParameters}
            selectedIds={form.labParameterIds}
            onChange={(ids) => onChange('labParameterIds', ids)}
            onConfigure={setReferenceParameter}
            error={shownErrors.labParameterIds}
          />
        </FormSection>
      )}

      {alertMessage && <FormAlert>{alertMessage}</FormAlert>}

      <div className="flex flex-col-reverse gap-3 rounded-2xl border border-slate-200/70 bg-white p-4 shadow-2xs sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={onCancel}
          disabled={submitting}
          className="inline-flex h-11 cursor-pointer items-center justify-center gap-2 rounded-xl border border-slate-200 px-5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <X aria-hidden="true" className="h-4 w-4" />
          Hủy
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="inline-flex h-11 cursor-pointer items-center justify-center gap-2 rounded-xl bg-[#127e7a] px-6 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#0e6b67] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#127e7a]/30 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Plus aria-hidden="true" className="h-4 w-4" />
          {submitting ? 'Đang lưu…' : 'Lưu dịch vụ'}
        </button>
      </div>
      </form>
      {referenceParameter && referenceRangeApi && (
        <LabReferenceRangeModal
          parameter={referenceParameter}
          api={referenceRangeApi}
          onChanged={onReferenceRangesChanged}
          onClose={() => setReferenceParameter(null)}
        />
      )}
    </>
  );
}

export default ServiceCreateView;
