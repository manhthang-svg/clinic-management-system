import { MAX_NAME_LENGTH, ethnicityOptions, occupationOptions } from './patientForm';
import { clinicToday } from '../visits/visitForm';

// form dung chung cho UC-37 tao ho so va UC-38 sua ho so
const inputClass =
    'mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 read-only:bg-slate-100';

const labelClass = 'block text-sm font-medium text-slate-700';

const sections = [
    {
        title: '1. Thông tin cá nhân',
        description: 'Thông tin định danh và liên hệ của bệnh nhân.',
    },
    {
        title: '2. Thông tin hành chính',
        description: 'Dân tộc, nghề nghiệp và địa chỉ cư trú.',
    },
    {
        title: '3. Thông tin người thân',
        description: 'Thông tin liên hệ người thân khi cần thiết.',
    },
];

// loi hien mau do duoi truong
export function FieldError({ name, error }) {
    return error ? (
        <p id={`${name}-error`} className="mt-1 text-xs font-medium text-red-600">{error}</p>
    ) : null;
}

export function Field({
    label,
    name,
    form,
    errors,
    onChange,
    required = false,
    ...props
}) {
    const error = errors?.[name];
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>

            <input
                id={name}
                name={name}
                value={form[name] ?? ''}
                onChange={onChange}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? `${name}-error` : undefined}
                className={`${inputClass} ${error ? 'border-red-400' : ''}`}
                {...props}
            />
            <FieldError name={name} error={error} />
        </div>
    );
}

export function SelectField({
    label,
    name,
    form,
    errors,
    onChange,
    options,
    required = false,
}) {
    const error = errors?.[name];
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>

            <select
                id={name}
                name={name}
                value={form[name] ?? ''}
                onChange={onChange}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? `${name}-error` : undefined}
                className={`${inputClass} ${error ? 'border-red-400' : ''}`}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
            <FieldError name={name} error={error} />
        </div>
    );
}

export function Dialog({ title, children }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div role="dialog" aria-modal="true" aria-label={title} className="w-full max-w-lg space-y-4 rounded-2xl bg-white p-6 shadow-xl">
                <h3 className="text-lg font-bold text-slate-800">{title}</h3>
                {children}
            </div>
        </div>
    );
}

// 3 phan cua form; dau bat buoc SDT / nguoi than theo tuoi
export function PatientFormSections({ form, fieldErrors, handleChange, adult, minor }) {
    return (
        <>
                {/* 1. Thong tin ca nhan */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[0].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[0].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <Field
                            label="Họ và tên"
                            name="hoten"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required
                            maxLength={MAX_NAME_LENGTH}
                            placeholder="Nhập họ và tên"
                        />

                        <Field
                            label="Ngày sinh"
                            name="ngaysinh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            type="date"
                            required
                            max={clinicToday()}
                        />

                        <SelectField
                            label="Giới tính"
                            name="gioitinh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required
                            options={[
                                { value: '1', label: 'Nam' },
                                { value: '2', label: 'Nữ' },
                                { value: '3', label: 'Chưa xác định' },
                            ]}
                        />

                        <Field
                            label="CCCD"
                            name="cccdSo"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            inputMode="numeric"
                            maxLength={12}
                            placeholder="12 chữ số"
                        />

                        <Field
                            label="Số hộ chiếu"
                            name="hochieuSo"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={15}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Số điện thoại"
                            name="sodienthoai"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            type="tel"
                            required={adult}
                            maxLength={10}
                            placeholder="10 chữ số, bắt buộc từ 18 tuổi"
                        />

                        <SelectField
                            label="Dân tộc"
                            name="dantoc"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required
                            options={ethnicityOptions(form.dantoc)}
                        />

                        <Field
                            label="Mã dân tộc"
                            name="dantocMa"
                            form={form}
                            errors={fieldErrors}
                            readOnly
                            title="Tự điền theo dân tộc đã chọn"
                            placeholder="Tự điền theo dân tộc"
                        />

                        <SelectField
                            label="Nhóm máu"
                            name="nhommau"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            options={[
                                { value: '', label: 'Chưa xác định' },
                                { value: 'A', label: 'A' },
                                { value: 'B', label: 'B' },
                                { value: 'AB', label: 'AB' },
                                { value: 'O', label: 'O' },
                            ]}
                        />

                        <Field
                            label="Yếu tố Rh"
                            name="yeutorh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={5}
                            placeholder="Ví dụ: Rh+"
                        />
                    </div>
                </section>

                {/* 2. Thong tin hanh chinh */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[1].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[1].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <SelectField
                            label="Nghề nghiệp"
                            name="nghenghiep"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            options={occupationOptions(form.nghenghiep)}
                        />

                        <Field
                            label="Mã nghề nghiệp"
                            name="nghenghiepMa"
                            form={form}
                            errors={fieldErrors}
                            readOnly
                            title="Tự điền theo nghề nghiệp đã chọn"
                            placeholder="Tự điền theo nghề nghiệp"
                        />

                        <Field
                            label="Nơi làm việc"
                            name="noilamviec"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            placeholder="Tên cơ quan hoặc nơi làm việc"
                        />

                        <Field
                            label="Ngoại kiều"
                            name="ngoaikieu"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={255}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Mã ngoại kiều"
                            name="ngoaikieuMa"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={2}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Xã/phường"
                            name="xaphuong"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            placeholder="Tên xã/phường"
                        />

                        <Field
                            label="Mã xã/phường"
                            name="xaphuongMa"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={5}
                            placeholder="Mã xã/phường"
                        />

                        <Field
                            label="Tỉnh/thành phố"
                            name="tinhthanh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            placeholder="Tên tỉnh/thành phố"
                        />

                        <Field
                            label="Mã tỉnh/thành"
                            name="tinhthanhMa"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={3}
                            placeholder="Mã tỉnh/thành"
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ"
                                name="diachi"
                                form={form}
                            errors={fieldErrors}
                                onChange={handleChange}
                                required
                                maxLength={1024}
                                placeholder="Số nhà, đường, thôn/xóm..."
                            />
                        </div>
                    </div>
                </section>

                {/* 3. Thong tin nguoi than */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[2].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[2].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <Field
                            label="Họ tên người thân"
                            name="hotennguoithan"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required={minor}
                            maxLength={255}
                            placeholder="Họ tên người liên hệ"
                        />

                        <SelectField
                            label="Quan hệ"
                            name="nguoithanQuanhe"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required={minor}
                            options={[
                                { value: '', label: 'Chọn quan hệ' },
                                { value: 'Bố', label: 'Bố' },
                                { value: 'Mẹ', label: 'Mẹ' },
                                { value: 'Vợ', label: 'Vợ' },
                                { value: 'Chồng', label: 'Chồng' },
                                { value: 'Con', label: 'Con' },
                                { value: 'Anh/chị/em', label: 'Anh/chị/em' },
                                { value: 'Khác', label: 'Khác' },
                            ]}
                        />

                        <Field
                            label="Số điện thoại người thân"
                            name="sodienthoainguoithan"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            type="tel"
                            maxLength={10}
                            placeholder="Số điện thoại liên hệ"
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ người thân"
                                name="diachinguoithan"
                                form={form}
                            errors={fieldErrors}
                                onChange={handleChange}
                                maxLength={1024}
                                placeholder="Địa chỉ người thân"
                            />
                        </div>
                    </div>
                </section>
        </>
    );
}
