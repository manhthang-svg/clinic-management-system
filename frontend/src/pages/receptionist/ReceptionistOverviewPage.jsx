
import { useMemo, useState } from 'react';

const initialPatients = [
    {
        id: 'BN0001234',
        name: 'Nguyễn Văn An',
        dob: '15/02/1990',
        gender: 'Nam',
        identity: '079188000123',
        phone: '0901 234 567',
        address: 'Phường Bến Thành, TP. Hồ Chí Minh',
        status: 'ACTIVE',
        visits: 5,
    },
    {
        id: 'BN0005678',
        name: 'Trần Thị Bích Ngọc',
        dob: '14/08/2010',
        gender: 'Nữ',
        identity: '079188000456',
        phone: '0902 345 678',
        address: 'Quận 1, TP. Hồ Chí Minh',
        status: 'COMPLETED',
        visits: 3,
    },
    {
        id: 'BN0009012',
        name: 'Lê Minh Hoàng',
        dob: '10/10/1985',
        gender: 'Nam',
        identity: '079188000789',
        phone: '0912 345 678',
        address: 'Quận 3, TP. Hồ Chí Minh',
        status: 'CANCELLED',
        visits: 1,
    },
];

const emptyForm = {
    name: '',
    dob: '',
    gender: 'Nam',
    identity: '',
    passport: '',
    phone: '',
    city: 'TP. Hồ Chí Minh',
    ward: '',
    address: '',
    occupation: '',
    ethnicity: 'Kinh',
    nationality: 'Việt Nam',
    guardianName: '',
    guardianRelation: '',
    guardianPhone: '',
    guardianAddress: '',
};

const statusMap = {
    ACTIVE: {
        label: 'Đang hoạt động',
        className: 'bg-emerald-50 text-emerald-700',
    },
    COMPLETED: {
        label: 'Đã hoàn thành',
        className: 'bg-slate-100 text-slate-600',
    },
    CANCELLED: {
        label: 'Đã hủy',
        className: 'bg-rose-50 text-rose-700',
    },
};

function SectionTitle({ children }) {
    return (
        <h3 className="mb-4 border-b border-slate-100 pb-3 text-sm font-bold text-[#174b70]">
            {children}
        </h3>
    );
}

function Field({
    label,
    value,
    onChange,
    placeholder,
    required = false,
    type = 'text',
    options,
    disabled = false,
}) {
    const className =
        'w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100';

    return (
        <label className="block min-w-0">
            <span className="mb-1.5 block text-xs font-medium text-slate-600">
                {label}
                {required && <span className="ml-1 text-rose-500">*</span>}
            </span>

            {options ? (
                <select
                    required={required}
                    disabled={disabled}
                    value={value}
                    onChange={onChange}
                    className={className}
                >
                    {options.map((option) => (
                        <option key={option} value={option}>
                            {option}
                        </option>
                    ))}
                </select>
            ) : (
                <input
                    required={required}
                    disabled={disabled}
                    type={type}
                    value={value}
                    placeholder={placeholder}
                    onChange={onChange}
                    className={className}
                />
            )}
        </label>
    );
}

function getInitials(name) {
    return name
        .trim()
        .split(/\s+/)
        .slice(-2)
        .map((part) => part[0])
        .join('')
        .toUpperCase();
}

export default function ReceptionistOverviewPage() {
    const [patients, setPatients] = useState(initialPatients);
    const [keyword, setKeyword] = useState('');
    const [searchBy, setSearchBy] = useState('Tất cả thông tin');
    const [selectedPatientId, setSelectedPatientId] = useState(null);
    const [pageMode, setPageMode] = useState('search');
    const [editingId, setEditingId] = useState(null);
    const [form, setForm] = useState(emptyForm);
    const [notice, setNotice] = useState('');

    const selectedPatient = patients.find(
        (patient) => patient.id === selectedPatientId,
    );

    const filteredPatients = useMemo(() => {
        const normalized = keyword.trim().toLocaleLowerCase('vi');

        if (!normalized) return patients;

        const fieldMap = {
            'Mã bệnh nhân': ['id'],
            'Họ và tên': ['name'],
            'CCCD/Định danh cá nhân': ['identity'],
            'Số điện thoại': ['phone'],
            'Tất cả thông tin': [
                'id',
                'name',
                'identity',
                'phone',
                'address',
            ],
        };

        const fields = fieldMap[searchBy] || fieldMap['Tất cả thông tin'];

        return patients.filter((patient) =>
            fields.some((field) =>
                String(patient[field] || '')
                    .toLocaleLowerCase('vi')
                    .includes(normalized),
            ),
        );
    }, [patients, keyword, searchBy]);

    function updateForm(field, value) {
        setForm((current) => ({ ...current, [field]: value }));
    }

    function openCreateForm() {
        setForm(emptyForm);
        setEditingId(null);
        setPageMode('create');
        setNotice('');
    }

    function openEditForm(patient) {
        setForm({
            ...emptyForm,
            ...patient,
        });
        setEditingId(patient.id);
        setPageMode('edit');
        setNotice('');
    }

    function openDetails(patient) {
        setSelectedPatientId(patient.id);
        setPageMode('details');
        setNotice('');
    }

    function handleSubmit(event) {
        event.preventDefault();

        if (form.identity && !/^\d{12}$/.test(form.identity)) {
            setNotice('CCCD phải gồm đúng 12 chữ số.');
            return;
        }

        if (!/^[0-9+\s().-]{9,15}$/.test(form.phone.trim())) {
            setNotice('Vui lòng kiểm tra số điện thoại.');
            return;
        }

        if (editingId) {
            setPatients((current) =>
                current.map((patient) =>
                    patient.id === editingId
                        ? { ...patient, ...form }
                        : patient,
                ),
            );

            setSelectedPatientId(editingId);
            setPageMode('details');
            setNotice('Đã cập nhật hồ sơ trong dữ liệu mẫu.');
            return;
        }

        const newPatient = {
            ...form,
            id: `BN${String(Date.now()).slice(-7)}`,
            status: 'ACTIVE',
            visits: 0,
        };

        setPatients((current) => [newPatient, ...current]);
        setKeyword(newPatient.id);
        setSearchBy('Tất cả thông tin');
        setPageMode('search');
        setNotice('Đã tạo hồ sơ bệnh nhân trong dữ liệu mẫu.');
    }

    const inputGrid = (
        <>
            <SectionTitle>1. Thông tin cá nhân</SectionTitle>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                <Field
                    label="Họ và tên"
                    required
                    value={form.name}
                    placeholder="Nhập họ và tên"
                    onChange={(e) => updateForm('name', e.target.value)}
                />

                <Field
                    label="Ngày sinh"
                    required
                    type="date"
                    value={form.dob}
                    onChange={(e) => updateForm('dob', e.target.value)}
                />

                <Field
                    label="Giới tính"
                    required
                    value={form.gender}
                    options={['Nam', 'Nữ', 'Khác']}
                    onChange={(e) => updateForm('gender', e.target.value)}
                />

                <Field
                    label="CCCD/Định danh cá nhân"
                    value={form.identity}
                    placeholder="Nhập số CCCD (12 chữ số)"
                    onChange={(e) => updateForm('identity', e.target.value)}
                />

                <Field
                    label="Hộ chiếu"
                    value={form.passport}
                    placeholder="Nhập số hộ chiếu"
                    onChange={(e) => updateForm('passport', e.target.value)}
                />

                <Field
                    label="Số điện thoại"
                    required
                    value={form.phone}
                    placeholder="Nhập số điện thoại"
                    onChange={(e) => updateForm('phone', e.target.value)}
                />

                <Field
                    label="Tỉnh/Thành phố"
                    value={form.city}
                    options={[
                        'TP. Hồ Chí Minh',
                        'Hà Nội',
                        'Đà Nẵng',
                        'Tỉnh khác',
                    ]}
                    onChange={(e) => updateForm('city', e.target.value)}
                />

                <Field
                    label="Phường/Xã"
                    value={form.ward}
                    placeholder="Nhập phường/xã"
                    onChange={(e) => updateForm('ward', e.target.value)}
                />

                <Field
                    label="Địa chỉ chi tiết"
                    required
                    value={form.address}
                    placeholder="Số nhà, đường..."
                    onChange={(e) => updateForm('address', e.target.value)}
                />

                <Field
                    label="Nghề nghiệp"
                    value={form.occupation}
                    options={[
                        'Nhân viên văn phòng',
                        'Học sinh',
                        'Sinh viên',
                        'Lao động tự do',
                        'Khác',
                    ]}
                    onChange={(e) => updateForm('occupation', e.target.value)}
                />

                <Field
                    label="Dân tộc"
                    value={form.ethnicity}
                    options={['Kinh', 'Tày', 'Thái', 'Mường', 'Khác']}
                    onChange={(e) => updateForm('ethnicity', e.target.value)}
                />

                <Field
                    label="Quốc tịch"
                    value={form.nationality}
                    options={['Việt Nam', 'Khác']}
                    onChange={(e) => updateForm('nationality', e.target.value)}
                />
            </div>

            <div className="mb-4 mt-7">
                <SectionTitle>
                    2. Thông tin người giám hộ
                    <span className="ml-1 font-normal text-slate-400">
                        (bắt buộc với bệnh nhân chưa đủ 18 tuổi)
                    </span>
                </SectionTitle>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
                <Field
                    label="Họ tên người giám hộ"
                    value={form.guardianName}
                    placeholder="Nhập họ tên"
                    onChange={(e) => updateForm('guardianName', e.target.value)}
                />

                <Field
                    label="Mối quan hệ"
                    value={form.guardianRelation}
                    options={['', 'Bố', 'Mẹ', 'Vợ/Chồng', 'Anh/Chị', 'Khác']}
                    onChange={(e) =>
                        updateForm('guardianRelation', e.target.value)
                    }
                />

                <Field
                    label="Số điện thoại"
                    value={form.guardianPhone}
                    placeholder="Nhập số điện thoại"
                    onChange={(e) =>
                        updateForm('guardianPhone', e.target.value)
                    }
                />

                <Field
                    label="Địa chỉ người giám hộ"
                    value={form.guardianAddress}
                    placeholder="Nhập địa chỉ"
                    onChange={(e) =>
                        updateForm('guardianAddress', e.target.value)
                    }
                />
            </div>
        </>
    );

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6 pb-8">
            {/* Page heading */}
            <header className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
                <div>
                    <div className="mb-2 flex items-center gap-2 text-xs text-slate-400">
                        <span>Trang chủ</span>
                        <span>/</span>
                        <span>Bệnh nhân</span>
                        {pageMode !== 'search' && (
                            <>
                                <span>/</span>
                                <span className="text-teal-700">
                                    {pageMode === 'create'
                                        ? 'Tạo hồ sơ mới'
                                        : pageMode === 'edit'
                                          ? 'Cập nhật thông tin'
                                          : 'Thông tin bệnh nhân'}
                                </span>
                            </>
                        )}
                    </div>

                    <h1 className="text-2xl font-bold tracking-tight text-slate-800">
                        {pageMode === 'search'
                            ? 'Tìm kiếm bệnh nhân'
                            : pageMode === 'create'
                              ? 'Tạo hồ sơ bệnh nhân mới'
                              : pageMode === 'edit'
                                ? 'Cập nhật thông tin bệnh nhân'
                                : 'Thông tin bệnh nhân'}
                    </h1>

                    <p className="mt-2 text-sm text-slate-500">
                        {pageMode === 'search'
                            ? 'Tra cứu thông tin hành chính và hồ sơ bệnh nhân.'
                            : pageMode === 'create'
                              ? 'Nhập thông tin hành chính để tạo hồ sơ bệnh nhân.'
                              : pageMode === 'edit'
                                ? 'Chỉnh sửa thông tin hành chính của bệnh nhân.'
                                : 'Xem thông tin hành chính và lịch sử khám bệnh.'}
                    </p>
                </div>

                {pageMode === 'search' && (
                    <button
                        type="button"
                        onClick={openCreateForm}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700"
                    >
                        <span className="text-lg">+</span>
                        Tạo hồ sơ bệnh nhân
                    </button>
                )}
            </header>

            {notice && (
                <div
                    role="status"
                    className="flex items-center justify-between gap-3 rounded-xl border border-teal-100 bg-teal-50 px-4 py-3 text-sm text-teal-800"
                >
                    {notice}
                    <button
                        type="button"
                        onClick={() => setNotice('')}
                        className="text-lg"
                        aria-label="Đóng thông báo"
                    >
                        ×
                    </button>
                </div>
            )}

            {/* Search and results */}
            {pageMode === 'search' && (
                <>
                    <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                        <h2 className="mb-4 text-sm font-bold text-slate-800">
                            Tìm kiếm theo
                        </h2>

                        <form
                            onSubmit={(event) => {
                                event.preventDefault();
                                setNotice(
                                    `Tìm thấy ${filteredPatients.length} bệnh nhân phù hợp.`,
                                );
                            }}
                            className="grid grid-cols-1 gap-3 lg:grid-cols-[220px_minmax(180px,1fr)_auto_auto]"
                        >
                            <select
                                value={searchBy}
                                onChange={(e) => setSearchBy(e.target.value)}
                                className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                            >
                                <option>Tất cả thông tin</option>
                                <option>Mã bệnh nhân</option>
                                <option>Họ và tên</option>
                                <option>CCCD/Định danh cá nhân</option>
                                <option>Số điện thoại</option>
                            </select>

                            <input
                                value={keyword}
                                onChange={(e) => setKeyword(e.target.value)}
                                placeholder="Nhập thông tin bệnh nhân cần tìm..."
                                className="min-w-0 rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                            />

                            <button
                                type="submit"
                                className="rounded-xl bg-[#0F9D9A] px-5 py-3 text-sm font-semibold text-white hover:bg-teal-700"
                            >
                                <span className="mr-2">⌕</span>
                                Tìm kiếm
                            </button>

                            <button
                                type="button"
                                onClick={() =>
                                    setNotice(
                                        'Chức năng quét CCCD cần tích hợp thiết bị hoặc thư viện đọc CCCD.',
                                    )
                                }
                                className="rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
                            >
                                ▣ Quét CCCD
                            </button>
                        </form>

                        <p className="mt-3 text-xs text-slate-400">
                            Có thể tìm theo mã bệnh nhân, họ tên, CCCD hoặc số
                            điện thoại.
                        </p>
                    </section>

                    <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                        <div className="flex flex-col justify-between gap-2 border-b border-slate-100 p-5 sm:flex-row sm:items-center">
                            <div>
                                <h2 className="font-bold text-slate-800">
                                    Kết quả tìm kiếm
                                    <span className="ml-2 rounded-full bg-teal-50 px-2.5 py-1 text-xs font-semibold text-teal-700">
                                        {filteredPatients.length}
                                    </span>
                                </h2>
                                <p className="mt-1 text-xs text-slate-500">
                                    Chọn mã bệnh nhân để xem hồ sơ chi tiết.
                                </p>
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[1000px] text-left">
                                <thead className="bg-slate-50">
                                    <tr className="text-xs text-slate-500">
                                        {[
                                            'Mã bệnh nhân',
                                            'Họ và tên',
                                            'Ngày sinh',
                                            'Giới tính',
                                            'CCCD/Định danh',
                                            'Số điện thoại',
                                            'Lượt khám',
                                            'Trạng thái',
                                        ].map((heading) => (
                                            <th
                                                key={heading}
                                                className="whitespace-nowrap px-4 py-4 font-semibold"
                                            >
                                                {heading}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>

                                <tbody className="divide-y divide-slate-100">
                                    {filteredPatients.map((patient) => {
                                        const status =
                                            statusMap[patient.status];

                                        return (
                                            <tr
                                                key={patient.id}
                                                className="transition hover:bg-teal-50/40"
                                            >
                                                <td className="px-4 py-4">
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            openDetails(patient)
                                                        }
                                                        className="whitespace-nowrap text-xs font-bold text-blue-700 hover:underline"
                                                    >
                                                        {patient.id}
                                                    </button>
                                                </td>
                                                <td className="whitespace-nowrap px-4 py-4 text-sm font-medium text-slate-700">
                                                    {patient.name}
                                                </td>
                                                <td className="whitespace-nowrap px-4 py-4 text-xs text-slate-600">
                                                    {patient.dob}
                                                </td>
                                                <td className="px-4 py-4 text-sm text-slate-600">
                                                    {patient.gender}
                                                </td>
                                                <td className="px-4 py-4 text-xs text-slate-600">
                                                    {patient.identity || '—'}
                                                </td>
                                                <td className="whitespace-nowrap px-4 py-4 text-xs text-slate-600">
                                                    {patient.phone}
                                                </td>
                                                <td className="px-4 py-4 text-center text-sm text-slate-600">
                                                    {patient.visits}
                                                </td>
                                                <td className="px-4 py-4">
                                                    <span
                                                        className={`whitespace-nowrap rounded-full px-2.5 py-1.5 text-xs font-semibold ${status.className}`}
                                                    >
                                                        {status.label}
                                                    </span>
                                                </td>
                                            </tr>
                                        );
                                    })}

                                    {filteredPatients.length === 0 && (
                                        <tr>
                                            <td
                                                colSpan={8}
                                                className="px-5 py-14 text-center"
                                            >
                                                <div className="text-3xl text-slate-300">
                                                    ⌕
                                                </div>
                                                <p className="mt-3 font-semibold text-slate-700">
                                                    Không tìm thấy bệnh nhân
                                                </p>
                                                <p className="mt-1 text-sm text-slate-500">
                                                    Kiểm tra lại từ khóa hoặc tạo
                                                    hồ sơ bệnh nhân mới.
                                                </p>
                                                <button
                                                    type="button"
                                                    onClick={openCreateForm}
                                                    className="mt-4 rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
                                                >
                                                    + Tạo hồ sơ mới
                                                </button>
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>

                        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-4 text-xs text-slate-500">
                            <span>
                                Hiển thị {filteredPatients.length} kết quả
                            </span>
                            <span>Dữ liệu minh họa</span>
                        </div>
                    </section>
                </>
            )}

            {/* Create / edit patient form */}
            {(pageMode === 'create' || pageMode === 'edit') && (
                <form
                    onSubmit={handleSubmit}
                    className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-7"
                >
                    {pageMode === 'edit' && (
                        <div className="mb-5 max-w-xs">
                            <Field
                                label="Mã bệnh nhân"
                                value={editingId}
                                disabled
                            />
                        </div>
                    )}

                    {inputGrid}

                    <div className="mt-7 flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
                        <button
                            type="button"
                            onClick={() => {
                                setPageMode(
                                    editingId ? 'details' : 'search',
                                );
                                setNotice('');
                            }}
                            className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                        >
                            Hủy
                        </button>

                        <button
                            type="submit"
                            className="rounded-xl bg-[#0F9D9A] px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-teal-700"
                        >
                            {editingId ? 'Lưu thay đổi' : 'Lưu hồ sơ'}
                        </button>
                    </div>
                </form>
            )}

            {/* Patient details */}
            {pageMode === 'details' && selectedPatient && (
                <div className="space-y-5">
                    <div className="flex flex-wrap justify-end gap-3">
                        <button
                            type="button"
                            onClick={() => openEditForm(selectedPatient)}
                            className="rounded-xl border border-teal-200 bg-white px-4 py-2.5 text-sm font-semibold text-teal-700 hover:bg-teal-50"
                        >
                            ✎ Cập nhật thông tin
                        </button>

                        <button
                            type="button"
                            onClick={() => {
                                setPageMode('search');
                                setNotice('');
                            }}
                            className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                        >
                            ← Quay lại danh sách
                        </button>
                    </div>

                    <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-7">
                        <SectionTitle>Thông tin hành chính</SectionTitle>

                        <div className="flex flex-col gap-5 border-b border-slate-100 pb-6 sm:flex-row sm:items-center">
                            <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-teal-50 text-xl font-bold text-teal-700">
                                {getInitials(selectedPatient.name)}
                            </div>

                            <div className="flex-1">
                                <h2 className="text-xl font-bold text-slate-800">
                                    {selectedPatient.name}
                                </h2>
                                <p className="mt-1 text-sm text-slate-500">
                                    Mã bệnh nhân: {selectedPatient.id}
                                </p>
                                <div className="mt-3 flex flex-wrap gap-2">
                                    <span className="rounded-full bg-teal-50 px-3 py-1 text-xs font-semibold text-teal-700">
                                        {selectedPatient.gender}
                                    </span>
                                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">
                                        {selectedPatient.dob || 'Chưa có ngày sinh'}
                                    </span>
                                </div>
                            </div>
                        </div>

                        <div className="grid grid-cols-1 gap-5 pt-6 sm:grid-cols-2 lg:grid-cols-3">
                            {[
                                ['CCCD/Định danh', selectedPatient.identity],
                                ['Hộ chiếu', selectedPatient.passport],
                                ['Số điện thoại', selectedPatient.phone],
                                ['Địa chỉ', selectedPatient.address],
                                ['Nghề nghiệp', selectedPatient.occupation],
                                ['Dân tộc', selectedPatient.ethnicity],
                                ['Quốc tịch', selectedPatient.nationality],
                                ['Người giám hộ', selectedPatient.guardianName],
                                ['SĐT người giám hộ', selectedPatient.guardianPhone],
                            ].map(([label, value]) => (
                                <div key={label}>
                                    <p className="text-xs text-slate-400">
                                        {label}
                                    </p>
                                    <p className="mt-1 break-words text-sm font-medium text-slate-700">
                                        {value || '—'}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                        <div className="border-b border-slate-100 p-5">
                            <h2 className="font-bold text-slate-800">
                                Lịch sử khám bệnh
                            </h2>
                            <p className="mt-1 text-sm text-slate-500">
                                Tổng lượt khám ghi nhận: {selectedPatient.visits}
                            </p>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[650px] text-left">
                                <thead className="bg-slate-50 text-xs text-slate-500">
                                    <tr>
                                        <th className="px-5 py-4 font-semibold">
                                            Mã đăng ký
                                        </th>
                                        <th className="px-5 py-4 font-semibold">
                                            Loại khám
                                        </th>
                                        <th className="px-5 py-4 font-semibold">
                                            Trạng thái
                                        </th>
                                        <th className="px-5 py-4 font-semibold">
                                            Phí thu
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {selectedPatient.visits > 0 ? (
                                        <tr className="border-t border-slate-100">
                                            <td className="px-5 py-4 text-sm font-medium text-blue-700">
                                                DK-{selectedPatient.id}
                                            </td>
                                            <td className="px-5 py-4 text-sm text-slate-600">
                                                Khám bệnh
                                            </td>
                                            <td className="px-5 py-4">
                                                <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700">
                                                    Hoàn thành
                                                </span>
                                            </td>
                                            <td className="px-5 py-4 text-sm text-slate-600">
                                                Theo hồ sơ thu phí
                                            </td>
                                        </tr>
                                    ) : (
                                        <tr>
                                            <td
                                                colSpan={4}
                                                className="px-5 py-10 text-center text-sm text-slate-500"
                                            >
                                                Chưa có lịch sử khám bệnh.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </section>
                </div>
            )}

            <p className="text-center text-xs text-slate-400">
                METPH · Hệ thống quản lý phòng khám · Giao diện dữ liệu mẫu
            </p>
        </div>
    );
}
