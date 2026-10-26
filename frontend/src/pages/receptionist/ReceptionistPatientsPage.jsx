import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Search, UserPlus, Eye, Pencil, X } from 'lucide-react';

const initialPatients = [
    {
        id: 'BN0001234',
        name: 'Nguyễn Văn An',
        dob: '1990-02-15',
        gender: 'Nam',
        phone: '0901234567',
        identity: '079188000123',
        status: 'ACTIVE',
    },
    {
        id: 'BN0005678',
        name: 'Trần Thị Bích Ngọc',
        dob: '2010-08-14',
        gender: 'Nữ',
        phone: '0902345678',
        identity: '079188000456',
        status: 'ACTIVE',
    },
    {
        id: 'BN0009012',
        name: 'Lê Minh Hoàng',
        dob: '1985-10-10',
        gender: 'Nam',
        phone: '0912345678',
        identity: '079188000789',
        status: 'INACTIVE',
    },
];

const statusOptions = {
    ACTIVE: {
        label: 'Đang hoạt động',
        className: 'bg-emerald-50 text-emerald-700',
    },
    INACTIVE: {
        label: 'Ngừng hoạt động',
        className: 'bg-slate-100 text-slate-600',
    },
};

const inputClass =
    'w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

export default function ReceptionistPatientsPage() {
    const navigate = useNavigate();

    const [patients] = useState(initialPatients);
    const [keyword, setKeyword] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [notice, setNotice] = useState('');

    const filteredPatients = useMemo(() => {
        const query = keyword.trim().toLowerCase();

        return patients.filter((patient) => {
            const matchesKeyword = [
                patient.id,
                patient.name,
                patient.phone,
                patient.identity,
            ].some((value) =>
                value.toLowerCase().includes(query),
            );

            const matchesStatus =
                statusFilter === 'ALL' ||
                patient.status === statusFilter;

            return matchesKeyword && matchesStatus;
        });
    }, [patients, keyword, statusFilter]);

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6">
            <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div>
                    <p className="mb-2 text-xs text-slate-400">
                        Trang chủ / Bệnh nhân
                    </p>

                    <h1 className="text-2xl font-bold text-slate-800">
                        Quản lý bệnh nhân
                    </h1>

                    <p className="mt-2 text-sm text-slate-500">
                        Tìm kiếm, xem và quản lý hồ sơ hành chính bệnh nhân.
                    </p>
                </div>

                <button
                    type="button"
                    onClick={() => navigate('/receptionist/patients/new')}
                    className="flex items-center justify-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-3 text-sm font-semibold text-white hover:bg-teal-700"
                >
                    <UserPlus size={18} />
                    Tạo hồ sơ mới
                </button>
            </header>

            {notice && (
                <div
                    role="status"
                    className="flex items-center justify-between rounded-xl border border-teal-100 bg-teal-50 px-4 py-3 text-sm text-teal-800"
                >
                    {notice}

                    <button
                        type="button"
                        onClick={() => setNotice('')}
                        aria-label="Đóng thông báo"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_220px]">
                    <div className="relative">
                        <Search
                            size={18}
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        />

                        <input
                            value={keyword}
                            onChange={(event) =>
                                setKeyword(event.target.value)
                            }
                            placeholder="Tìm mã bệnh nhân, họ tên, CCCD, số điện thoại..."
                            className={`${inputClass} pl-10`}
                        />
                    </div>

                    <select
                        value={statusFilter}
                        onChange={(event) =>
                            setStatusFilter(event.target.value)
                        }
                        className={inputClass}
                        aria-label="Lọc trạng thái bệnh nhân"
                    >
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="ACTIVE">Đang hoạt động</option>
                        <option value="INACTIVE">Ngừng hoạt động</option>
                    </select>
                </div>
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-100 p-5">
                    <div>
                        <h2 className="font-bold text-slate-800">
                            Danh sách bệnh nhân
                        </h2>

                        <p className="mt-1 text-sm text-slate-500">
                            Có {filteredPatients.length} kết quả
                        </p>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[850px] text-left">
                        <thead className="bg-slate-50 text-xs text-slate-500">
                            <tr>
                                {[
                                    'Mã bệnh nhân',
                                    'Họ và tên',
                                    'Ngày sinh',
                                    'Giới tính',
                                    'Số điện thoại',
                                    'Trạng thái',
                                    'Thao tác',
                                ].map((heading) => (
                                    <th
                                        key={heading}
                                        className="whitespace-nowrap px-5 py-4 font-semibold"
                                    >
                                        {heading}
                                    </th>
                                ))}
                            </tr>
                        </thead>

                        <tbody className="divide-y divide-slate-100">
                            {filteredPatients.map((patient) => {
                                const status = statusOptions[patient.status];

                                return (
                                    <tr
                                        key={patient.id}
                                        className="hover:bg-teal-50/30"
                                    >
                                        <td className="px-5 py-4 text-sm font-semibold text-blue-700">
                                            {patient.id}
                                        </td>

                                        <td className="px-5 py-4 text-sm font-medium text-slate-700">
                                            {patient.name}
                                        </td>

                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {patient.dob
                                                ? new Date(
                                                    `${patient.dob}T00:00:00`,
                                                ).toLocaleDateString('vi-VN')
                                                : '—'}
                                        </td>

                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {patient.gender}
                                        </td>

                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {patient.phone}
                                        </td>

                                        <td className="px-5 py-4">
                                            <span
                                                className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-semibold ${status.className}`}
                                            >
                                                {status.label}
                                            </span>
                                        </td>

                                        <td className="px-5 py-4">
                                            <div className="flex items-center gap-2">
                                                <button
                                                    type="button"
                                                    title="Xem hồ sơ"
                                                    onClick={() =>
                                                        navigate(
                                                            `/receptionist/patients/${patient.id}`,
                                                        )
                                                    }
                                                    className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50"
                                                >
                                                    <Eye size={16} />
                                                </button>

                                                <button
                                                    type="button"
                                                    title="Chỉnh sửa"
                                                    onClick={() =>
                                                        setNotice(
                                                            'Chức năng chỉnh sửa hồ sơ chưa được triển khai.',
                                                        )
                                                    }
                                                    className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50"
                                                >
                                                    <Pencil size={16} />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}

                            {filteredPatients.length === 0 && (
                                <tr>
                                    <td
                                        colSpan={7}
                                        className="px-5 py-12 text-center text-sm text-slate-500"
                                    >
                                        Không tìm thấy bệnh nhân phù hợp.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    );
}
