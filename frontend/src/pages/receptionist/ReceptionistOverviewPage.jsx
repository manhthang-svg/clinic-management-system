import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
    Search,
    UserPlus,
    CalendarPlus,
    RefreshCw,
    Building2,
    Eye,
    CheckCircle2,
    X,
    User,
    Phone,
    CreditCard,
    MapPin,
    AlertCircle,
    ArrowRight,
    Users,
} from 'lucide-react';
import patientApi from '../../apis/patientApi';
import visitApi from '../../apis/visitApi';
import { PRIORITY_REASON_OPTIONS } from '../../constants/visitData';

function formatGender(gender) {
    if (gender === 1 || gender === '1') return 'Nam';
    if (gender === 2 || gender === '2') return 'Nữ';
    return 'Khác';
}

function formatDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const [y, m, d] = dateStr.split('-');
        if (y && m && d) return `${d}/${m}/${y}`;
        return dateStr;
    } catch {
        return dateStr;
    }
}

function calculateAge(dateStr) {
    if (!dateStr) return '';
    try {
        const birthYear = parseInt(dateStr.split('-')[0], 10);
        if (!birthYear) return '';
        const currentYear = new Date().getFullYear();
        const age = currentYear - birthYear;
        return age >= 0 ? ` (${age} tuổi)` : '';
    } catch {
        return '';
    }
}

export default function ReceptionistOverviewPage() {
    const navigate = useNavigate();
    const location = useLocation();

    const [patients, setPatients] = useState([]);
    const [totalPatients, setTotalPatients] = useState(0);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState('');
    const [searchInput, setSearchInput] = useState('');
    const [activeRoomsCount, setActiveRoomsCount] = useState(0);

    const [notice, setNotice] = useState('');
    const [error, setError] = useState('');

    // Modal xem chi tiết hồ sơ bệnh nhân
    const [selectedPatient, setSelectedPatient] = useState(null);

    // Modal Đăng ký khám (Tạo Visit)
    const [visitPatient, setVisitPatient] = useState(null);
    const [rooms, setRooms] = useState([]);
    const [selectedRoomId, setSelectedRoomId] = useState('');
    const [lydovaovien, setLydovaovien] = useState('Khám nội tổng quát / theo yêu cầu');
    const [isPriority, setIsPriority] = useState(false);
    const [priorityReason, setPriorityReason] = useState(PRIORITY_REASON_OPTIONS[0][0]);
    const [submittingVisit, setSubmittingVisit] = useState(false);
    const [visitSuccessData, setVisitSuccessData] = useState(null);

    // Tải danh sách bệnh nhân
    const fetchPatients = useCallback(async (searchQuery = '') => {
        setLoading(true);
        setError('');
        try {
            const res = await patientApi.list({
                keyword: searchQuery || undefined,
                size: 50,
            });
            const list =
                res?.content ||
                res?.data?.content ||
                (Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []);
            const total =
                res?.totalElements ?? res?.data?.totalElements ?? list.length;
            setPatients(list);
            setTotalPatients(total);
        } catch (err) {
            setError(err?.message || 'Không thể tải danh sách bệnh nhân.');
        } finally {
            setLoading(false);
        }
    }, []);

    // Tải số phòng khám đang hoạt động
    const fetchRooms = useCallback(async () => {
        try {
            const res = await visitApi.getActiveExaminationRooms();
            const list = res?.data || res || [];
            setRooms(list);
            setActiveRoomsCount(list.length);
            if (list.length > 0 && !selectedRoomId) {
                setSelectedRoomId(list[0].id);
            }
        } catch (err) {
            console.error('Không thể tải phòng khám:', err);
        }
    }, [selectedRoomId]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPatients(keyword);
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPatients, keyword]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchRooms();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchRooms]);

    // Nhận state từ trang Tạo bệnh nhân mới (nếu có)
    useEffect(() => {
        const timer = window.setTimeout(() => {
            if (location.state?.notice) {
                setNotice(location.state.notice);
            }
            if (location.state?.openVisitFor) {
                void handleOpenVisitModal(location.state.openVisitFor);
            }
        }, 0);
        return () => window.clearTimeout(timer);
    }, [location.state]);

    // Mở modal đăng ký khám cho bệnh nhân
    async function handleOpenVisitModal(patient) {
        setVisitPatient(patient);
        setLydovaovien('Khám nội tổng quát / theo yêu cầu');
        setIsPriority(false);
        setPriorityReason(PRIORITY_REASON_OPTIONS[0][0]);
        setVisitSuccessData(null);

        try {
            const res = await visitApi.getActiveExaminationRooms();
            const roomList = res?.data || res || [];
            setRooms(roomList);
            if (roomList.length > 0) {
                setSelectedRoomId(roomList[0].id);
            }
        } catch (err) {
            console.error('Lỗi tải phòng khám', err);
        }
    }

    // Xác nhận gửi lượt khám cho Thu ngân
    const handleConfirmVisit = async (e) => {
        e.preventDefault();
        if (!selectedRoomId) {
            alert('Vui lòng chọn phòng khám bác sĩ.');
            return;
        }
        if (!lydovaovien.trim()) {
            alert('Vui lòng nhập lý do khám.');
            return;
        }

        setSubmittingVisit(true);
        try {
            const res = await visitApi.create({
                patientId: visitPatient.id,
                roomId: selectedRoomId,
                doituongbnLoai: 'DV', // Khám dịch vụ
                lydovaovien: lydovaovien.trim(),
                isPriority,
                priorityReason: isPriority ? priorityReason : null,
            });

            const data = res?.data || res;
            const chosenRoom = rooms.find((r) => r.id === selectedRoomId);

            setVisitSuccessData({
                sovaovien: data?.sovaovien || '—',
                queueNumber: data?.queueNumber || '—',
                roomName: chosenRoom?.name || 'Phòng khám chỉ định',
                patientName: visitPatient.hoten,
            });

            setNotice(
                `Đã tạo đơn đi khám thành công cho ${visitPatient.hoten}! Số vào viện: ${data?.sovaovien || '—'}. Đã chuyển sang Thu ngân để tạo đơn & thu tiền khám Lần 1.`
            );
        } catch (err) {
            alert(err?.message || 'Không thể tạo đơn đi khám. Vui lòng thử lại.');
        } finally {
            setSubmittingVisit(false);
        }
    };

    const handleSearchSubmit = (e) => {
        e.preventDefault();
        setKeyword(searchInput.trim());
    };

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6 pb-10">
            {/* Header */}
            <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div>
                    <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-teal-700">
                        <span>METPH</span>
                        <span>/</span>
                        <span>BỘ PHẬN TIẾP ĐÓN</span>
                    </div>
                    <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
                        Tổng quan tiếp đón & Đăng ký khám
                    </h1>
                    <p className="mt-1 text-sm text-slate-500">
                        Tiếp nhận bệnh nhân đăng ký, tạo đơn đi khám (Visit) và chuyển thông tin sang quầy Thu ngân thu tiền khám Lần 1.
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                    <button
                        type="button"
                        onClick={() => fetchPatients(keyword)}
                        className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
                    >
                        <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin text-teal-600' : 'text-slate-500'}`} />
                        Làm mới
                    </button>
                    <button
                        type="button"
                        onClick={() => navigate('/receptionist/patients/new')}
                        className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700"
                    >
                        <UserPlus className="h-4 w-4" />
                        Tiếp nhận bệnh nhân mới
                    </button>
                </div>
            </header>

            {/* Thông báo banner */}
            {notice && (
                <div className="flex items-center justify-between gap-3 rounded-2xl border border-teal-200 bg-teal-50/80 p-4 text-sm text-teal-900 shadow-sm">
                    <div className="flex items-center gap-3">
                        <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-teal-600" />
                        <span className="font-medium">{notice}</span>
                    </div>
                    <button
                        type="button"
                        onClick={() => setNotice('')}
                        className="rounded-lg p-1 text-teal-700 hover:bg-teal-100"
                    >
                        <X className="h-4 w-4" />
                    </button>
                </div>
            )}

            {error && (
                <div className="flex items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 shadow-sm">
                    <div className="flex items-center gap-3">
                        <AlertCircle className="h-5 w-5 flex-shrink-0 text-rose-600" />
                        <span>{error}</span>
                    </div>
                    <button
                        type="button"
                        onClick={() => setError('')}
                        className="rounded-lg p-1 text-rose-700 hover:bg-rose-100"
                    >
                        <X className="h-4 w-4" />
                    </button>
                </div>
            )}

            {/* Thống kê nhanh */}
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
                        <Users className="h-6 w-6" />
                    </div>
                    <div>
                        <p className="text-xs font-medium text-slate-500">Hồ sơ bệnh nhân đã đăng ký</p>
                        <p className="mt-1 text-2xl font-bold tracking-tight text-slate-900">{totalPatients}</p>
                    </div>
                </div>

                <div className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
                        <Building2 className="h-6 w-6" />
                    </div>
                    <div>
                        <p className="text-xs font-medium text-slate-500">Phòng khám bác sĩ đang mở</p>
                        <p className="mt-1 text-2xl font-bold tracking-tight text-slate-900">{activeRoomsCount} phòng</p>
                    </div>
                </div>

                <div className="flex items-center justify-between rounded-2xl border border-teal-100 bg-gradient-to-r from-teal-500 to-teal-700 p-5 text-white shadow-sm">
                    <div>
                        <p className="text-xs font-medium text-teal-100">Quy trình dịch vụ (Lần 1)</p>
                        <p className="mt-1 text-sm font-semibold">Tạo đơn khám ➔ Chuyển Thu ngân</p>
                        <p className="mt-0.5 text-xs text-teal-100">Thu ngân thu tiền ➔ Bệnh nhân vào khám</p>
                    </div>
                    <CalendarPlus className="h-8 w-8 text-teal-200" />
                </div>
            </section>

            {/* Tìm kiếm bệnh nhân */}
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <div className="relative flex-1">
                        <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            type="text"
                            value={searchInput}
                            onChange={(e) => setSearchInput(e.target.value)}
                            placeholder="Tìm kiếm bệnh nhân theo họ tên, mã BN, số CCCD hoặc số điện thoại..."
                            className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-4 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-100"
                        />
                    </div>
                    <button
                        type="submit"
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800"
                    >
                        <Search className="h-4 w-4" />
                        Tìm kiếm
                    </button>
                    {keyword && (
                        <button
                            type="button"
                            onClick={() => {
                                setSearchInput('');
                                setKeyword('');
                            }}
                            className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
                        >
                            <X className="h-4 w-4" />
                            Xóa bộ lọc
                        </button>
                    )}
                </form>
            </section>

            {/* Bảng danh sách bệnh nhân */}
            <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="flex flex-col justify-between gap-2 border-b border-slate-100 p-5 sm:flex-row sm:items-center">
                    <div>
                        <h2 className="text-base font-bold text-slate-900">
                            Danh sách bệnh nhân tiếp đón
                            <span className="ml-2.5 rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-semibold text-teal-700">
                                {patients.length} bệnh nhân
                            </span>
                        </h2>
                        <p className="mt-1 text-xs text-slate-500">
                            Chọn <strong>"Tạo đơn đi khám"</strong> để đăng ký lượt khám và gửi ngay sang quầy Thu ngân thu tiền Lần 1.
                        </p>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[950px] text-left text-sm">
                        <thead className="bg-slate-50/80 text-xs font-semibold uppercase tracking-wider text-slate-500">
                            <tr>
                                <th className="px-5 py-3.5">Mã bệnh nhân</th>
                                <th className="px-5 py-3.5">Họ và tên</th>
                                <th className="px-5 py-3.5">Ngày sinh / Tuổi</th>
                                <th className="px-5 py-3.5">Giới tính</th>
                                <th className="px-5 py-3.5">CCCD / Định danh</th>
                                <th className="px-5 py-3.5">Số điện thoại</th>
                                <th className="px-5 py-3.5">Địa chỉ</th>
                                <th className="px-5 py-3.5 text-right">Thao tác</th>
                            </tr>
                        </thead>

                        <tbody className="divide-y divide-slate-100 text-slate-700">
                            {loading ? (
                                <tr>
                                    <td colSpan={8} className="py-12 text-center text-slate-500">
                                        <RefreshCw className="mx-auto h-6 w-6 animate-spin text-teal-600" />
                                        <p className="mt-2 text-sm font-medium">Đang tải danh sách bệnh nhân...</p>
                                    </td>
                                </tr>
                            ) : patients.length === 0 ? (
                                <tr>
                                    <td colSpan={8} className="py-14 text-center">
                                        <User className="mx-auto h-8 w-8 text-slate-300" />
                                        <p className="mt-2 text-base font-semibold text-slate-700">Không tìm thấy bệnh nhân</p>
                                        <p className="text-sm text-slate-400">Thử tìm kiếm với từ khóa khác hoặc tạo hồ sơ bệnh nhân mới.</p>
                                        <button
                                            type="button"
                                            onClick={() => navigate('/receptionist/patients/new')}
                                            className="mt-4 inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white hover:bg-teal-700"
                                        >
                                            <UserPlus className="h-4 w-4" />
                                            Tạo hồ sơ bệnh nhân
                                        </button>
                                    </td>
                                </tr>
                            ) : (
                                patients.map((patient) => (
                                    <tr key={patient.id} className="transition hover:bg-teal-50/30">
                                        <td className="px-5 py-4">
                                            <span className="font-mono text-xs font-bold text-teal-700">
                                                {patient.mabenhnhan || patient.id?.slice(0, 8)}
                                            </span>
                                        </td>
                                        <td className="px-5 py-4 font-semibold text-slate-900">
                                            {patient.hoten}
                                        </td>
                                        <td className="px-5 py-4 text-xs text-slate-600">
                                            {formatDate(patient.ngaysinh)}
                                            <span className="text-slate-400">{calculateAge(patient.ngaysinh)}</span>
                                        </td>
                                        <td className="px-5 py-4">
                                            <span
                                                className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                                                    patient.gioitinh === 1 || patient.gioitinh === '1'
                                                        ? 'bg-blue-50 text-blue-700'
                                                        : patient.gioitinh === 2 || patient.gioitinh === '2'
                                                          ? 'bg-pink-50 text-pink-700'
                                                          : 'bg-slate-100 text-slate-600'
                                                }`}
                                            >
                                                {formatGender(patient.gioitinh)}
                                            </span>
                                        </td>
                                        <td className="px-5 py-4 font-mono text-xs text-slate-600">
                                            {patient.cccdSo || '—'}
                                        </td>
                                        <td className="px-5 py-4 text-xs font-medium text-slate-800">
                                            {patient.sodienthoai || '—'}
                                        </td>
                                        <td className="max-w-[220px] truncate px-5 py-4 text-xs text-slate-500" title={patient.diachi}>
                                            {patient.diachi || '—'}
                                        </td>
                                        <td className="px-5 py-4 text-right">
                                            <div className="flex items-center justify-end gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => handleOpenVisitModal(patient)}
                                                    className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-teal-700"
                                                    title="Tạo đơn đi khám cho bệnh nhân và chuyển sang Thu ngân"
                                                >
                                                    <CalendarPlus className="h-3.5 w-3.5" />
                                                    Tạo đơn đi khám
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => setSelectedPatient(patient)}
                                                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
                                                    title="Xem chi tiết hồ sơ bệnh nhân"
                                                >
                                                    <Eye className="h-3.5 w-3.5" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </section>

            {/* MODAL: TẠO ĐƠN ĐI KHÁM (VISIT) VÀ CHUYỂN SANG THU NGÂN */}
            {visitPatient && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
                    <div className="relative w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl">
                        {/* Header modal */}
                        <div className="border-b border-slate-100 bg-teal-700 px-6 py-4 text-white">
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2.5">
                                    <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-teal-800/80">
                                        <CalendarPlus className="h-5 w-5 text-teal-100" />
                                    </div>
                                    <div>
                                        <h3 className="font-bold">Tạo đơn đi khám (Visit)</h3>
                                        <p className="text-xs text-teal-100">Chuyển thông tin sang Thu ngân để thu tiền Lần 1</p>
                                    </div>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setVisitPatient(null);
                                        setVisitSuccessData(null);
                                    }}
                                    className="rounded-lg p-1 text-teal-100 hover:bg-teal-800 hover:text-white"
                                >
                                    <X className="h-5 w-5" />
                                </button>
                            </div>
                        </div>

                        {/* Nội dung modal */}
                        <div className="p-6">
                            {visitSuccessData ? (
                                <div className="space-y-5 text-center">
                                    <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                                        <CheckCircle2 className="h-10 w-10" />
                                    </div>
                                    <div>
                                        <h4 className="text-lg font-bold text-slate-900">Tạo lượt khám thành công!</h4>
                                        <p className="mt-1 text-sm text-slate-500">
                                            Đơn khám đã được chuyển tự động sang quầy Thu ngân.
                                        </p>
                                    </div>

                                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-left text-xs space-y-2">
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Bệnh nhân:</span>
                                            <span className="font-bold text-slate-800">{visitSuccessData.patientName}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Số vào viện:</span>
                                            <span className="font-mono font-bold text-teal-700">{visitSuccessData.sovaovien}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Số thứ tự khám:</span>
                                            <span className="font-bold text-slate-800">STT #{visitSuccessData.queueNumber}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Phòng khám chỉ định:</span>
                                            <span className="font-semibold text-slate-800">{visitSuccessData.roomName}</span>
                                        </div>
                                    </div>

                                    <div className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800 text-left border border-amber-200">
                                        📌 <strong>Hướng dẫn bệnh nhân:</strong> Vui lòng mời bệnh nhân di chuyển qua <strong>Quầy Thu Ngân</strong> để nộp tiền khám (Lần 1). Sau khi hoàn tất, bác sĩ tại phòng khám sẽ gọi vào khám.
                                    </div>

                                    <button
                                        type="button"
                                        onClick={() => {
                                            setVisitPatient(null);
                                            setVisitSuccessData(null);
                                        }}
                                        className="w-full rounded-xl bg-teal-600 py-3 text-sm font-semibold text-white shadow-sm hover:bg-teal-700"
                                    >
                                        Hoàn tất & Đóng
                                    </button>
                                </div>
                            ) : (
                                <form onSubmit={handleConfirmVisit} className="space-y-4">
                                    {/* Thông tin bệnh nhân */}
                                    <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5 text-xs text-slate-700 space-y-1.5">
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Bệnh nhân:</span>
                                            <span className="font-bold text-slate-900">{visitPatient.hoten}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Mã bệnh nhân:</span>
                                            <span className="font-mono text-slate-700">{visitPatient.mabenhnhan || visitPatient.id?.slice(0, 8)}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Ngày sinh & Giới tính:</span>
                                            <span>{formatDate(visitPatient.ngaysinh)} ({formatGender(visitPatient.gioitinh)})</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Số điện thoại:</span>
                                            <span>{visitPatient.sodienthoai || '—'}</span>
                                        </div>
                                    </div>

                                    {/* Chọn phòng khám bác sĩ */}
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-700">
                                            Phòng khám chỉ định <span className="text-rose-500">*</span>
                                        </label>
                                        <select
                                            value={selectedRoomId}
                                            onChange={(e) => setSelectedRoomId(e.target.value)}
                                            required
                                            className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                        >
                                            {rooms.length === 0 ? (
                                                <option value="">Không có phòng khám khả dụng</option>
                                            ) : (
                                                rooms.map((room) => (
                                                    <option key={room.id} value={room.id}>
                                                        {room.name} {room.roomNumber ? `(${room.roomNumber})` : ''} — Khoa: {room.departmentName || 'Chung'}
                                                    </option>
                                                ))
                                            )}
                                        </select>
                                    </div>

                                    {/* Lý do vào viện / khám */}
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-700">
                                            Lý do vào viện / Triệu chứng khám <span className="text-rose-500">*</span>
                                        </label>
                                        <input
                                            type="text"
                                            value={lydovaovien}
                                            onChange={(e) => setLydovaovien(e.target.value)}
                                            required
                                            placeholder="Nhập lý do hoặc triệu chứng..."
                                            className="mt-1.5 w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                        />
                                    </div>

                                    {/* Ưu tiên */}
                                    <div className="space-y-2 pt-1">
                                        <div className="flex items-center gap-2">
                                            <input
                                                type="checkbox"
                                                id="priorityCheck"
                                                checked={isPriority}
                                                onChange={(e) => setIsPriority(e.target.checked)}
                                                className="h-4 w-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                                            />
                                            <label htmlFor="priorityCheck" className="text-xs font-semibold text-slate-700">
                                                Ưu tiên tiếp đón & khám bệnh
                                            </label>
                                        </div>

                                        {isPriority && (
                                            <div className="ml-6 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs">
                                                <label className="block font-semibold text-amber-900">
                                                    Lý do ưu tiên <span className="text-rose-500">*</span>
                                                </label>
                                                <select
                                                    value={priorityReason}
                                                    onChange={(e) => setPriorityReason(e.target.value)}
                                                    className="mt-1 w-full rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-medium text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                                >
                                                    {PRIORITY_REASON_OPTIONS.map(([value, label]) => (
                                                        <option key={value} value={value}>{label}</option>
                                                    ))}
                                                </select>
                                            </div>
                                        )}
                                    </div>

                                    {/* Banner thông báo luồng tiếp theo */}
                                    <div className="rounded-xl bg-teal-50 p-3 text-xs text-teal-800 border border-teal-200">
                                        💡 Hệ thống sẽ tự động tạo phiếu thu tiền khám (Lần 1) và chuyển ngay sang quầy Thu ngân.
                                    </div>

                                    {/* Nút gửi */}
                                    <div className="flex items-center justify-end gap-3 pt-2">
                                        <button
                                            type="button"
                                            onClick={() => setVisitPatient(null)}
                                            className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                                        >
                                            Hủy
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={submittingVisit || rooms.length === 0}
                                            className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700 disabled:opacity-50"
                                        >
                                            {submittingVisit ? (
                                                <RefreshCw className="h-4 w-4 animate-spin" />
                                            ) : (
                                                <ArrowRight className="h-4 w-4" />
                                            )}
                                            Tạo lượt khám & Chuyển Thu ngân
                                        </button>
                                    </div>
                                </form>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL: CHI TIẾT BỆNH NHÂN */}
            {selectedPatient && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
                    <div className="relative w-full max-w-2xl overflow-hidden rounded-2xl bg-white shadow-2xl">
                        <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-6 py-4">
                            <div className="flex items-center gap-3">
                                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-100 text-teal-700 font-bold">
                                    {selectedPatient.hoten?.charAt(0) || 'BN'}
                                </div>
                                <div>
                                    <h3 className="text-base font-bold text-slate-900">{selectedPatient.hoten}</h3>
                                    <p className="font-mono text-xs text-teal-700">Mã BN: {selectedPatient.mabenhnhan || selectedPatient.id}</p>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => setSelectedPatient(null)}
                                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-200"
                            >
                                <X className="h-5 w-5" />
                            </button>
                        </div>

                        <div className="max-h-[70vh] overflow-y-auto p-6 space-y-6">
                            <div>
                                <h4 className="border-b border-slate-100 pb-2 text-xs font-bold uppercase tracking-wider text-slate-400">
                                    1. Thông tin cá nhân & định danh
                                </h4>
                                <div className="mt-3 grid grid-cols-2 gap-4 text-xs sm:grid-cols-3">
                                    <div>
                                        <span className="text-slate-400">Ngày sinh:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{formatDate(selectedPatient.ngaysinh)}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Giới tính:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{formatGender(selectedPatient.gioitinh)}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Số CCCD / CMND:</span>
                                        <p className="mt-0.5 font-mono font-medium text-slate-800">{selectedPatient.cccdSo || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Số hộ chiếu:</span>
                                        <p className="mt-0.5 font-mono font-medium text-slate-800">{selectedPatient.hochieuSo || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Số điện thoại:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.sodienthoai || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Dân tộc:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.dantoc || 'Kinh'}</p>
                                    </div>
                                </div>
                            </div>

                            <div>
                                <h4 className="border-b border-slate-100 pb-2 text-xs font-bold uppercase tracking-wider text-slate-400">
                                    2. Địa chỉ cư trú & nghề nghiệp
                                </h4>
                                <div className="mt-3 grid grid-cols-1 gap-4 text-xs sm:grid-cols-2">
                                    <div>
                                        <span className="text-slate-400">Địa chỉ:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.diachi || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Nghề nghiệp:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.nghenghiep || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Nơi làm việc:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.noilamviec || '—'}</p>
                                    </div>
                                </div>
                            </div>

                            <div>
                                <h4 className="border-b border-slate-100 pb-2 text-xs font-bold uppercase tracking-wider text-slate-400">
                                    3. Thông tin người thân liên hệ
                                </h4>
                                <div className="mt-3 grid grid-cols-2 gap-4 text-xs sm:grid-cols-3">
                                    <div>
                                        <span className="text-slate-400">Họ tên người thân:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.hotennguoithan || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">Quan hệ:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.nguoithanQuanhe || '—'}</p>
                                    </div>
                                    <div>
                                        <span className="text-slate-400">SĐT người thân:</span>
                                        <p className="mt-0.5 font-medium text-slate-800">{selectedPatient.sodienthoainguoithan || '—'}</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50 px-6 py-4">
                            <button
                                type="button"
                                onClick={() => setSelectedPatient(null)}
                                className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                            >
                                Đóng
                            </button>
                            <button
                                type="button"
                                onClick={() => {
                                    const p = selectedPatient;
                                    setSelectedPatient(null);
                                    handleOpenVisitModal(p);
                                }}
                                className="inline-flex items-center gap-1.5 rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-teal-700"
                            >
                                <CalendarPlus className="h-4 w-4" />
                                Đăng ký khám cho bệnh nhân này
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
