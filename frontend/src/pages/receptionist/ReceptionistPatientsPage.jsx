import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
    Search,
    UserPlus,
    Eye,
    Pencil,
    X,
    CalendarPlus,
    CheckCircle2,
    RefreshCw,
    Building2,
    AlertCircle,
} from 'lucide-react';
import patientApi from '../../apis/patientApi';
import visitApi from '../../apis/visitApi';

const inputClass =
    'w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

export default function ReceptionistPatientsPage() {
    const navigate = useNavigate();
    const location = useLocation();

    const [patients, setPatients] = useState([]);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [notice, setNotice] = useState('');

    // Modal Đăng ký khám (Tạo Visit)
    const [registerPatient, setRegisterPatient] = useState(null);
    const [rooms, setRooms] = useState([]);
    const [selectedRoomId, setSelectedRoomId] = useState('');
    const [lydovaovien, setLydovaovien] = useState('Khám nội tổng quát');
    const [isPriority, setIsPriority] = useState(false);
    const [priorityReason, setPriorityReason] = useState('ELDERLY');
    const [submittingVisit, setSubmittingVisit] = useState(false);

    // Tải danh sách bệnh nhân
    const fetchPatients = useCallback(async () => {
        setLoading(true);
        try {
            const res = await patientApi.list({ size: 50 });
            const list = res?.data?.content || res?.content || (Array.isArray(res?.data) ? res.data : []);
            setPatients(list);
        } catch (err) {
            setNotice(err?.message || 'Không thể tải danh sách bệnh nhân');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPatients();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPatients]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            if (location.state?.notice) {
                setNotice(location.state.notice);
            }
            if (location.state?.openVisitFor) {
                void handleOpenRegisterModal(location.state.openVisitFor);
            }
        }, 0);
        return () => window.clearTimeout(timer);
    }, [location.state]);

    // Tải danh sách phòng khám khi mở modal đăng ký khám
    async function handleOpenRegisterModal(patient) {
        setRegisterPatient(patient);
        setLydovaovien('Khám nội tổng quát');
        setIsPriority(false);
        setPriorityReason('ELDERLY');
        try {
            const res = await visitApi.getActiveExaminationRooms();
            const roomList = res?.data || res || [];
            setRooms(roomList);
            if (roomList.length > 0) {
                setSelectedRoomId(roomList[0].id);
            }
        } catch (err) {
            console.error('Không thể tải danh sách phòng khám', err);
        }
    }

    // Gửi yêu cầu đăng ký khám
    const handleSubmitVisit = async (e) => {
        e.preventDefault();
        if (!selectedRoomId) {
            alert('Vui lòng chọn phòng khám');
            return;
        }
        if (!lydovaovien.trim()) {
            alert('Vui lòng nhập lý do vào viện');
            return;
        }

        setSubmittingVisit(true);
        try {
            const res = await visitApi.create({
                patientId: registerPatient.id,
                roomId: selectedRoomId,
                doituongbnLoai: 'DV', // Khám dịch vụ
                lydovaovien: lydovaovien.trim(),
                isPriority,
                priorityReason: isPriority ? priorityReason : null,
            });
            const data = res?.data || res;
            setNotice(
                `Đăng ký khám thành công cho bệnh nhân ${registerPatient.hoten || registerPatient.name}! ` +
                `Số vào viện: ${data?.sovaovien || '—'}, STT: ${data?.queueNumber || '—'}. ` +
                `Vui lòng hướng dẫn bệnh nhân qua Quầy Thu Ngân để nộp phí khám (Lần 1).`
            );
            setRegisterPatient(null);
        } catch (err) {
            alert(err?.message || 'Không thể tạo lượt khám');
        } finally {
            setSubmittingVisit(false);
        }
    };

    const filteredPatients = useMemo(() => {
        const query = keyword.trim().toLowerCase();
        return patients.filter((patient) => {
            const name = patient.hoten || patient.name || '';
            const code = patient.mabenhnhan || patient.id || '';
            const phone = patient.sodienthoai || patient.phone || '';
            const cccd = patient.cccdSo || patient.identity || '';

            return (
                !query ||
                [name, code, phone, cccd].some((value) =>
                    value.toLowerCase().includes(query)
                )
            );
        });
    }, [patients, keyword]);

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6">
            <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div>
                    <p className="mb-2 text-xs text-slate-400">Trang chủ / Tiếp đón bệnh nhân</p>
                    <h1 className="text-2xl font-bold text-slate-800">Quản lý & Tiếp đón bệnh nhân</h1>
                    <p className="mt-1 text-sm text-slate-500">
                        Tìm kiếm hồ sơ bệnh nhân, đăng ký lượt khám và chuyển thu ngân thanh toán phí khám lần 1.
                    </p>
                </div>

                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={fetchPatients}
                        disabled={loading}
                        className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    >
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                        Làm mới
                    </button>
                    <button
                        type="button"
                        onClick={() => navigate('/receptionist/patients/new')}
                        className="flex items-center justify-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 shadow-sm"
                    >
                        <UserPlus size={18} />
                        Tạo hồ sơ bệnh nhân mới
                    </button>
                </div>
            </header>

            {notice && (
                <div
                    role="status"
                    className="flex items-center justify-between rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-800"
                >
                    <div className="flex items-center gap-2">
                        <CheckCircle2 size={18} className="text-teal-600 shrink-0" />
                        <span>{notice}</span>
                    </div>
                    <button type="button" onClick={() => setNotice('')} className="p-1 hover:bg-teal-100 rounded-lg">
                        <X size={16} />
                    </button>
                </div>
            )}

            <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_220px]">
                    <div className="relative">
                        <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        <input
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                            placeholder="Tìm mã bệnh nhân, họ tên, CCCD, số điện thoại..."
                            className={`${inputClass} pl-10`}
                        />
                    </div>

                    <select
                        value={statusFilter}
                        onChange={(event) => setStatusFilter(event.target.value)}
                        className={inputClass}
                    >
                        <option value="ALL">Tất cả bệnh nhân</option>
                        <option value="ACTIVE">Đang hoạt động</option>
                    </select>
                </div>
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-100 p-5">
                    <div>
                        <h2 className="font-bold text-slate-800">Danh sách bệnh nhân</h2>
                        <p className="mt-1 text-sm text-slate-500">Có {filteredPatients.length} bệnh nhân</p>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[850px] text-left">
                        <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
                            <tr>
                                {['Mã BN', 'Họ và tên', 'Ngày sinh', 'Giới tính', 'Số điện thoại', 'Thao tác'].map((heading) => (
                                    <th key={heading} className="whitespace-nowrap px-5 py-4 font-semibold">
                                        {heading}
                                    </th>
                                ))}
                            </tr>
                        </thead>

                        <tbody className="divide-y divide-slate-100">
                            {filteredPatients.map((patient) => {
                                const name = patient.hoten || patient.name;
                                const code = patient.mabenhnhan || patient.id;
                                const dob = patient.ngaysinh || patient.dob;
                                const gender =
                                    patient.gioitinh === 1 ? 'Nam' : patient.gioitinh === 2 ? 'Nữ' : patient.gender || '—';
                                const phone = patient.sodienthoai || patient.phone || '—';

                                return (
                                    <tr key={patient.id} className="hover:bg-teal-50/30 transition">
                                        <td className="px-5 py-4 text-sm font-semibold font-mono text-teal-700">{code}</td>
                                        <td className="px-5 py-4 text-sm font-semibold text-slate-800">{name}</td>
                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {dob ? new Date(`${dob}T00:00:00`).toLocaleDateString('vi-VN') : '—'}
                                        </td>
                                        <td className="px-5 py-4 text-sm text-slate-600">{gender}</td>
                                        <td className="px-5 py-4 text-sm text-slate-600">{phone}</td>
                                        <td className="px-5 py-4">
                                            <div className="flex items-center gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => handleOpenRegisterModal(patient)}
                                                    className="inline-flex items-center gap-1.5 rounded-lg bg-[#0F9D9A] px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-teal-700"
                                                    title="Đăng ký khám cho bệnh nhân"
                                                >
                                                    <CalendarPlus size={14} />
                                                    Đăng ký khám
                                                </button>
                                                <button
                                                    type="button"
                                                    title="Xem hồ sơ"
                                                    onClick={() => navigate(`/receptionist/patients/${patient.id}`)}
                                                    className="rounded-lg border border-slate-200 p-1.5 text-slate-600 hover:bg-slate-50"
                                                >
                                                    <Eye size={15} />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}

                            {filteredPatients.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="px-5 py-12 text-center text-sm text-slate-500">
                                        {loading ? 'Đang tải danh sách bệnh nhân...' : 'Không tìm thấy bệnh nhân phù hợp.'}
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </section>

            {/* MODAL ĐĂNG KÝ KHÁM (TẠO VISIT) */}
            {registerPatient && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                    <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                            <div>
                                <h3 className="text-lg font-bold text-slate-800">Đăng ký lượt khám bệnh mới</h3>
                                <p className="text-xs text-slate-500">
                                    Tạo lượt khám dịch vụ & gửi yêu cầu thanh toán lần 1 tới Quầy Thu Ngân
                                </p>
                            </div>
                            <button onClick={() => setRegisterPatient(null)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                                <X size={18} />
                            </button>
                        </div>

                        <form onSubmit={handleSubmitVisit} className="mt-4 space-y-4">
                            {/* Bệnh nhân info */}
                            <div className="rounded-xl bg-slate-50 p-4 border border-slate-100 space-y-1">
                                <p className="text-sm font-bold text-slate-800">
                                    {registerPatient.hoten || registerPatient.name}
                                </p>
                                <p className="text-xs text-slate-500">
                                    Mã BN: <strong className="font-mono text-slate-700">{registerPatient.mabenhnhan || registerPatient.id}</strong>{' '}
                                    • SĐT: {registerPatient.sodienthoai || registerPatient.phone || '—'}
                                </p>
                            </div>

                            {/* Phòng khám */}
                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1">
                                    Chọn phòng khám bệnh <span className="text-red-500">*</span>
                                </label>
                                <select
                                    value={selectedRoomId}
                                    onChange={(e) => setSelectedRoomId(e.target.value)}
                                    required
                                    className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-teal-500"
                                >
                                    {rooms.map((r) => (
                                        <option key={r.id} value={r.id}>
                                            {r.roomCode} - {r.roomName} ({r.location || 'Khu khám'})
                                        </option>
                                    ))}
                                    {rooms.length === 0 && <option value="">Đang tải danh sách phòng khám...</option>}
                                </select>
                            </div>

                            {/* Đối tượng */}
                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1">Hình thức khám</label>
                                <div className="rounded-xl border border-teal-200 bg-teal-50/50 p-3 text-sm font-semibold text-teal-800">
                                    Khám Dịch Vụ (DV) - Tự chi trả 2 lần (Phí khám + Phí CLS)
                                </div>
                            </div>

                            {/* Lý do vào viện */}
                            <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1">
                                    Lý do vào viện / Triệu chứng <span className="text-red-500">*</span>
                                </label>
                                <input
                                    value={lydovaovien}
                                    onChange={(e) => setLydovaovien(e.target.value)}
                                    placeholder="VD: Khám tổng quát, Đau đầu, Sốt nhẹ..."
                                    required
                                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm outline-none focus:border-teal-500"
                                />
                            </div>

                            {/* Ưu tiên */}
                            <div className="space-y-2 pt-1">
                                <label className="flex items-center gap-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={isPriority}
                                        onChange={(e) => setIsPriority(e.target.checked)}
                                        className="h-4 w-4 rounded text-teal-600 focus:ring-teal-500"
                                    />
                                    <span className="text-sm font-medium text-slate-700">Ưu tiên tiếp đón & khám bệnh</span>
                                </label>

                                {isPriority && (
                                    <div className="ml-6 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs">
                                        <label className="block font-semibold text-amber-900">
                                            Lý do ưu tiên <span className="text-red-500">*</span>
                                        </label>
                                        <select
                                            value={priorityReason}
                                            onChange={(e) => setPriorityReason(e.target.value)}
                                            className="mt-1 w-full rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-medium text-slate-800 outline-none focus:border-teal-500"
                                        >
                                            <option value="ELDERLY">Người cao tuổi (≥ 75 tuổi)</option>
                                            <option value="FRAIL_DISABLED">Người khuyết tật nặng / suy kiệt</option>
                                        </select>
                                    </div>
                                )}
                            </div>

                            <div className="mt-6 flex justify-end gap-3 border-t border-slate-100 pt-3">
                                <button
                                    type="button"
                                    onClick={() => setRegisterPatient(null)}
                                    className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="submit"
                                    disabled={submittingVisit}
                                    className="inline-flex items-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 shadow-sm"
                                >
                                    <CheckCircle2 size={16} />
                                    {submittingVisit ? 'Đang tiếp nhận...' : 'Hoàn tất & Gửi Thu Ngân'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
