
import { useMemo, useState } from 'react';
import {
    CalendarDays,
    Search,
    Clock3,
    CheckCircle2,
    XCircle,
} from 'lucide-react';

const initialAppointments = [
    {
        id: 'DKK260001',
        patientId: 'BN0001234',
        patientName: 'Nguyễn Văn An',
        phone: '0901234567',
        date: '2026-10-26',
        time: '08:00',
        department: 'Nội tổng hợp',
        doctor: 'BS. Trần Minh Đức',
        status: 'WAITING',
    },
    {
        id: 'DKK260002',
        patientId: 'BN0005678',
        patientName: 'Trần Thị Bích Ngọc',
        phone: '0902345678',
        date: '2026-10-26',
        time: '08:30',
        department: 'Nội tổng hợp',
        doctor: 'BS. Lê Thị Hương',
        status: 'CONFIRMED',
    },
    {
        id: 'DKK260003',
        patientId: 'BN0009012',
        patientName: 'Lê Minh Hoàng',
        phone: '0912345678',
        date: '2026-10-26',
        time: '09:00',
        department: 'Tai Mũi Họng',
        doctor: 'Chưa phân công',
        status: 'CANCELLED',
    },
];

const statusMap = {
    WAITING: {
        label: 'Chờ xác nhận',
        className: 'bg-amber-50 text-amber-700',
    },
    CONFIRMED: {
        label: 'Đã xác nhận',
        className: 'bg-emerald-50 text-emerald-700',
    },
    CANCELLED: {
        label: 'Đã hủy',
        className: 'bg-rose-50 text-rose-700',
    },
};

const inputClass =
    'w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

export default function ReceptionistAppointmentsPage() {
    const [appointments, setAppointments] = useState(initialAppointments);
    const [keyword, setKeyword] = useState('');
    const [dateFilter, setDateFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [notice, setNotice] = useState('');

    const filteredAppointments = useMemo(() => {
        const query = keyword.trim().toLowerCase();

        return appointments.filter((appointment) => {
            const matchesKeyword = [
                appointment.id,
                appointment.patientId,
                appointment.patientName,
                appointment.phone,
                appointment.department,
                appointment.doctor,
            ].some((value) => value.toLowerCase().includes(query));

            const matchesDate =
                !dateFilter || appointment.date === dateFilter;

            const matchesStatus =
                statusFilter === 'ALL' ||
                appointment.status === statusFilter;

            return matchesKeyword && matchesDate && matchesStatus;
        });
    }, [appointments, keyword, dateFilter, statusFilter]);

    const waitingCount = appointments.filter(
        (item) => item.status === 'WAITING',
    ).length;

    const confirmedCount = appointments.filter(
        (item) => item.status === 'CONFIRMED',
    ).length;

    const cancelledCount = appointments.filter(
        (item) => item.status === 'CANCELLED',
    ).length;

    function updateStatus(id, status) {
        setAppointments((current) =>
            current.map((item) =>
                item.id === id ? { ...item, status } : item,
            ),
        );

        setNotice(
            status === 'CONFIRMED'
                ? 'Đã xác nhận lịch khám trong dữ liệu mẫu.'
                : 'Đã hủy lịch khám trong dữ liệu mẫu.',
        );
    }

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6">
            <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div>
                    <p className="mb-2 text-xs text-slate-400">
                        Trang chủ / Lịch khám
                    </p>
                    <h1 className="text-2xl font-bold text-slate-800">
                        Quản lý lịch khám
                    </h1>
                    <p className="mt-2 text-sm text-slate-500">
                        Tra cứu và theo dõi lịch đăng ký khám của bệnh nhân.
                    </p>
                </div>
            </header>

            {notice && (
                <div
                    role="status"
                    className="rounded-xl border border-teal-100 bg-teal-50 px-4 py-3 text-sm text-teal-800"
                >
                    {notice}
                </div>
            )}

            <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                {[
                    {
                        label: 'Chờ xác nhận',
                        value: waitingCount,
                        icon: Clock3,
                        color: 'bg-amber-50 text-amber-700',
                    },
                    {
                        label: 'Đã xác nhận',
                        value: confirmedCount,
                        icon: CheckCircle2,
                        color: 'bg-emerald-50 text-emerald-700',
                    },
                    {
                        label: 'Đã hủy',
                        value: cancelledCount,
                        icon: XCircle,
                        color: 'bg-rose-50 text-rose-700',
                    },
                ].map((item) => {
                    const Icon = item.icon;

                    return (
                        <div
                            key={item.label}
                            className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm"
                        >
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-500">
                                    {item.label}
                                </span>
                                <span
                                    className={`flex h-10 w-10 items-center justify-center rounded-xl ${item.color}`}
                                >
                                    <Icon size={20} />
                                </span>
                            </div>
                            <p className="mt-3 text-3xl font-bold text-slate-800">
                                {item.value}
                            </p>
                        </div>
                    );
                })}
            </section>

            <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <div className="mb-4 flex items-center gap-2">
                    <CalendarDays size={19} className="text-teal-700" />
                    <h2 className="font-bold text-slate-800">
                        Bộ lọc lịch khám
                    </h2>
                </div>

                <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <div className="relative">
                        <Search
                            size={17}
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        />
                        <input
                            value={keyword}
                            onChange={(event) =>
                                setKeyword(event.target.value)
                            }
                            placeholder="Tên, mã bệnh nhân, mã đăng ký..."
                            className={`${inputClass} pl-9`}
                        />
                    </div>

                    <input
                        type="date"
                        aria-label="Lọc ngày khám"
                        value={dateFilter}
                        onChange={(event) =>
                            setDateFilter(event.target.value)
                        }
                        className={inputClass}
                    />

                    <select
                        value={statusFilter}
                        onChange={(event) =>
                            setStatusFilter(event.target.value)
                        }
                        aria-label="Lọc trạng thái lịch khám"
                        className={inputClass}
                    >
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="WAITING">Chờ xác nhận</option>
                        <option value="CONFIRMED">Đã xác nhận</option>
                        <option value="CANCELLED">Đã hủy</option>
                    </select>
                </div>
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                <div className="border-b border-slate-100 p-5">
                    <h2 className="font-bold text-slate-800">
                        Danh sách lịch khám
                    </h2>
                    <p className="mt-1 text-sm text-slate-500">
                        {filteredAppointments.length} lịch khám phù hợp
                    </p>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[1000px] text-left">
                        <thead className="bg-slate-50 text-xs text-slate-500">
                            <tr>
                                {[
                                    'Mã đăng ký',
                                    'Bệnh nhân',
                                    'Ngày khám',
                                    'Giờ khám',
                                    'Chuyên khoa',
                                    'Bác sĩ',
                                    'Trạng thái',
                                    'Thao tác',
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
                            {filteredAppointments.map((appointment) => {
                                const status =
                                    statusMap[appointment.status];

                                return (
                                    <tr
                                        key={appointment.id}
                                        className="hover:bg-teal-50/30"
                                    >
                                        <td className="px-4 py-4 text-xs font-bold text-blue-700">
                                            {appointment.id}
                                        </td>

                                        <td className="px-4 py-4">
                                            <p className="whitespace-nowrap text-sm font-semibold text-slate-700">
                                                {appointment.patientName}
                                            </p>
                                            <p className="mt-1 text-xs text-slate-400">
                                                {appointment.patientId}
                                            </p>
                                        </td>

                                        <td className="whitespace-nowrap px-4 py-4 text-sm text-slate-600">
                                            {new Date(
                                                `${appointment.date}T00:00:00`,
                                            ).toLocaleDateString('vi-VN')}
                                        </td>

                                        <td className="px-4 py-4 text-sm font-medium text-slate-700">
                                            {appointment.time}
                                        </td>

                                        <td className="px-4 py-4 text-sm text-slate-600">
                                            {appointment.department}
                                        </td>

                                        <td className="px-4 py-4 text-sm text-slate-600">
                                            {appointment.doctor}
                                        </td>

                                        <td className="px-4 py-4">
                                            <span
                                                className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-semibold ${status.className}`}
                                            >
                                                {status.label}
                                            </span>
                                        </td>

                                        <td className="px-4 py-4">
                                            {appointment.status === 'WAITING' ? (
                                                <div className="flex gap-2">
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            updateStatus(
                                                                appointment.id,
                                                                'CONFIRMED',
                                                            )
                                                        }
                                                        className="rounded-lg bg-teal-600 px-3 py-2 text-xs font-semibold text-white hover:bg-teal-700"
                                                    >
                                                        Xác nhận
                                                    </button>

                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            updateStatus(
                                                                appointment.id,
                                                                'CANCELLED',
                                                            )
                                                        }
                                                        className="rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                                                    >
                                                        Hủy
                                                    </button>
                                                </div>
                                            ) : (
                                                <span className="text-xs text-slate-400">
                                                    Không có thao tác
                                                </span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}

                            {filteredAppointments.length === 0 && (
                                <tr>
                                    <td
                                        colSpan={8}
                                        className="px-5 py-12 text-center text-sm text-slate-500"
                                    >
                                        Không tìm thấy lịch khám phù hợp.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </section>

            <p className="text-center text-xs text-slate-400">
                METPH · Dữ liệu lịch khám minh họa
            </p>
        </div>
    );
}
