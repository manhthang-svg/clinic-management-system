import { useNavigate } from 'react-router';
import { ArrowLeft, Save } from 'lucide-react';
import { useCreatePatient } from '../../hooks/useCreatePatient';

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

function Field({
    label,
    name,
    form,
    onChange,
    required = false,
    ...props
}) {
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
                required={required}
                className={inputClass}
                {...props}
            />
        </div>
    );
}

function SelectField({
    label,
    name,
    form,
    onChange,
    options,
    required = false,
}) {
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
                required={required}
                className={inputClass}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </div>
    );
}

export default function ReceptionistCreatePatientPage() {
    const navigate = useNavigate();

    // Hook quản lý form, validation, trạng thái lưu và gọi API.
    const {
        form,
        error,
        submitting,
        handleChange,
        handleSubmit,
        goBack,
    } = useCreatePatient();

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

            <form onSubmit={handleSubmit} className="space-y-5">
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
                            onChange={handleChange}
                            required
                            maxLength={255}
                            placeholder="Nhập họ và tên"
                        />

                        <Field
                            label="Ngày sinh"
                            name="ngaysinh"
                            form={form}
                            onChange={handleChange}
                            type="date"
                            required
                            max={new Date().toISOString().slice(0, 10)}
                        />

                        <SelectField
                            label="Giới tính"
                            name="gioitinh"
                            form={form}
                            onChange={handleChange}
                            required
                            options={[
                                { value: '1', label: 'Nam' },
                                { value: '2', label: 'Nữ' },
                                { value: '3', label: 'Khác' },
                            ]}
                        />

                        <Field
                            label="CCCD"
                            name="cccdSo"
                            form={form}
                            onChange={handleChange}
                            inputMode="numeric"
                            maxLength={12}
                            placeholder="12 chữ số"
                        />

                        <Field
                            label="Số hộ chiếu"
                            name="hochieuSo"
                            form={form}
                            onChange={handleChange}
                            maxLength={15}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Số điện thoại"
                            name="sodienthoai"
                            form={form}
                            onChange={handleChange}
                            type="tel"
                            maxLength={15}
                            placeholder="0912345678"
                        />

                        <SelectField
                            label="Dân tộc"
                            name="dantoc"
                            form={form}
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
                            onChange={handleChange}
                            required
                            maxLength={2}
                            placeholder="Ví dụ: 01"
                        />

                        <SelectField
                            label="Nhóm máu"
                            name="nhommau"
                            form={form}
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
                            onChange={handleChange}
                            maxLength={100}
                            placeholder="Nhập nghề nghiệp"
                        />

                        <Field
                            label="Mã nghề nghiệp"
                            name="nghenghiepMa"
                            form={form}
                            onChange={handleChange}
                            maxLength={5}
                            placeholder="Mã nghề nghiệp"
                        />

                        <Field
                            label="Nơi làm việc"
                            name="noilamviec"
                            form={form}
                            onChange={handleChange}
                            placeholder="Tên cơ quan hoặc nơi làm việc"
                        />

                        <Field
                            label="Ngoại kiều"
                            name="ngoaikieu"
                            form={form}
                            onChange={handleChange}
                            maxLength={255}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Mã ngoại kiều"
                            name="ngoaikieuMa"
                            form={form}
                            onChange={handleChange}
                            maxLength={2}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Xã/phường"
                            name="xaphuong"
                            form={form}
                            onChange={handleChange}
                            placeholder="Tên xã/phường"
                        />

                        <Field
                            label="Mã xã/phường"
                            name="xaphuongMa"
                            form={form}
                            onChange={handleChange}
                            maxLength={5}
                            placeholder="Mã xã/phường"
                        />

                        <Field
                            label="Tỉnh/thành phố"
                            name="tinhthanh"
                            form={form}
                            onChange={handleChange}
                            placeholder="Tên tỉnh/thành phố"
                        />

                        <Field
                            label="Mã tỉnh/thành"
                            name="tinhthanhMa"
                            form={form}
                            onChange={handleChange}
                            maxLength={3}
                            placeholder="Mã tỉnh/thành"
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ"
                                name="diachi"
                                form={form}
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
                            onChange={handleChange}
                            maxLength={255}
                            placeholder="Họ tên người liên hệ"
                        />

                        <SelectField
                            label="Quan hệ"
                            name="nguoithanQuanhe"
                            form={form}
                            onChange={handleChange}
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
                            onChange={handleChange}
                            type="tel"
                            maxLength={15}
                            placeholder="Số điện thoại liên hệ"
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ người thân"
                                name="diachinguoithan"
                                form={form}
                                onChange={handleChange}
                                maxLength={1024}
                                placeholder="Địa chỉ người thân"
                            />
                        </div>
                    </div>
                </section>

                {error && (
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
                        {submitting ? 'Đang lưu...' : 'Lưu hồ sơ'}
                    </button>
                </div>
            </form>
        </div>
    );
}
