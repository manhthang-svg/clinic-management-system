import { ArrowLeft, Save } from 'lucide-react';
import { useCreatePatient } from '../../hooks/useCreatePatient';
import { ADULT_AGE, MAX_NAME_LENGTH, MSG, formAge, msg57 } from '../../components/patients/patientForm';
import { clinicToday } from '../../components/visits/visitForm';

const inputClass =
    'mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100';

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

// MSG02/MSG05/MSG08 in red under the field (UC-37 EF-01).
function FieldError({ name, error }) {
    return error ? (
        <p id={`${name}-error`} className="mt-1 text-xs font-medium text-red-600">{error}</p>
    ) : null;
}

function Field({
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

function SelectField({
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

function Dialog({ title, children }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div role="dialog" aria-modal="true" aria-label={title} className="w-full max-w-lg space-y-4 rounded-2xl bg-white p-6 shadow-xl">
                <h3 className="text-lg font-bold text-slate-800">{title}</h3>
                {children}
            </div>
        </div>
    );
}

/** UC-37 Create New Patient Profile. */
export default function ReceptionistCreatePatientPage({ patientApi }) {
    // Hook quản lý form, validation, trạng thái lưu và gọi API.
    const {
        form,
        fieldErrors,
        error,
        conflict,
        duplicates,
        confirmCancel,
        submitting,
        handleChange,
        handleSubmit,
        goBack,
        openProfile,
        createAnyway,
        closeDuplicates,
        keepEditing,
        discard,
    } = useCreatePatient(patientApi);

    // Step 4: required fields depend on the age (phone from 18; guardian under 18).
    const age = formAge(form, clinicToday());
    const adult = age !== null && age >= ADULT_AGE;
    const minor = age !== null && age < ADULT_AGE;

    return (
        <div className="mx-auto w-full max-w-[1500px] space-y-6">
            <header className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <div className="mb-2 flex items-center gap-2 text-xs text-slate-400">
                        <span>Bệnh nhân</span>
                        <span>/</span>
                        <span className="text-teal-700">
                            Tạo hồ sơ bệnh nhân mới
                        </span>
                    </div>

                    <h1 className="text-2xl font-bold tracking-tight text-slate-800">
                        Tạo hồ sơ bệnh nhân mới
                    </h1>

                    <p className="mt-1 text-sm text-slate-500">
                        Nhập thông tin hành chính theo hồ sơ bệnh nhân.
                    </p>
                </div>

                <button
                    type="button"
                    onClick={goBack}
                    disabled={submitting}
                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
                >
                    <ArrowLeft size={16} />
                    Quay lại
                </button>
            </header>

            <form onSubmit={handleSubmit} noValidate className="space-y-5">
                {/* 1. Thông tin cá nhân */}
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
                            options={[
                                { value: 'Kinh', label: 'Kinh' },
                                { value: 'Tày', label: 'Tày' },
                                { value: 'Thái', label: 'Thái' },
                                { value: 'Mường', label: 'Mường' },
                                { value: 'Khmer', label: 'Khmer' },
                                { value: 'Hoa', label: 'Hoa' },
                                { value: 'Nùng', label: 'Nùng' },
                                { value: 'Khác', label: 'Khác' },
                            ]}
                        />

                        <Field
                            label="Mã dân tộc"
                            name="dantocMa"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            required
                            maxLength={2}
                            placeholder="Ví dụ: 01"
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

                {/* 2. Thông tin hành chính */}
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
                        <Field
                            label="Nghề nghiệp"
                            name="nghenghiep"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={100}
                            placeholder="Nhập nghề nghiệp"
                        />

                        <Field
                            label="Mã nghề nghiệp"
                            name="nghenghiepMa"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            maxLength={5}
                            placeholder="Mã nghề nghiệp"
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

                {/* 3. Thông tin người thân */}
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

                {conflict && (
                    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        <span>{fieldErrors[conflict.field]}</span>
                        <button
                            type="button"
                            onClick={() => openProfile(conflict.patientId)}
                            className="rounded-lg border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100"
                        >
                            Mở hồ sơ
                        </button>
                    </div>
                )}

                {error && !conflict && (
                    <div
                        role="alert"
                        className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                    >
                        {error}
                    </div>
                )}

                <div className="flex flex-wrap justify-end gap-3 pb-6">
                    <button
                        type="button"
                        onClick={goBack}
                        disabled={submitting}
                        className="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
                    >
                        Hủy
                    </button>

                    <button
                        type="submit"
                        disabled={submitting}
                        className="inline-flex items-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        <Save size={16} />
                        {submitting ? 'Đang lưu…' : 'Lưu hồ sơ'}
                    </button>
                </div>
            </form>

            {duplicates && (
                <Dialog title="Có thể trùng hồ sơ">
                    <p className="text-sm text-slate-600">{msg57(duplicates.length)}</p>
                    <ul className="max-h-60 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-100">
                        {duplicates.map((patient) => (
                            <li key={patient.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                                <span>
                                    <span className="font-mono text-teal-700">{patient.mabenhnhan}</span>
                                    {' · '}{patient.hoten}
                                    {patient.sodienthoai ? ` · ${patient.sodienthoai}` : ''}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => openProfile(patient.id)}
                                    className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                >
                                    Chọn hồ sơ này
                                </button>
                            </li>
                        ))}
                    </ul>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={closeDuplicates} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">
                            Quay lại chỉnh sửa
                        </button>
                        <button
                            type="button"
                            onClick={() => void createAnyway()}
                            disabled={submitting}
                            className="rounded-xl bg-[#0F9D9A] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
                        >
                            Vẫn tạo hồ sơ mới
                        </button>
                    </div>
                </Dialog>
            )}

            {confirmCancel && (
                <Dialog title="Hủy tạo hồ sơ">
                    <p className="text-sm text-slate-600">{MSG.DISCARD}</p>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={keepEditing} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">
                            Tiếp tục chỉnh sửa
                        </button>
                        <button type="button" onClick={discard} className="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white">
                            Loại bỏ
                        </button>
                    </div>
                </Dialog>
            )}
        </div>
    );
}
