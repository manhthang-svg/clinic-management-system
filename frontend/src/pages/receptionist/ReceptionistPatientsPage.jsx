import { useCallback, useEffect, useMemo, useState } from 'react';
import { generatePath, useLocation, useNavigate } from 'react-router';
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
import { ROUTES } from '../../routes/routes';

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
    const [editingPatient, setEditingPatient] = useState(null);
    const [editForm, setEditForm] = useState(null);
    const [savingPatient, setSavingPatient] = useState(false);

    // UC-40: đăng ký khám mở trang riêng thay vì popup.
    const openRegisterVisit = useCallback(
        (patient) => navigate(generatePath(ROUTES.RECEPTIONIST.VISIT_REGISTER, { id: patient.id })),
        [navigate]
    );

    // Tải danh sách bệnh nhân
    const fetchPatients = useCallback(async () => {
        setLoading(true);
        try {
            const res = await patientApi.list({ size: 50, keyword: keyword.trim() });
            const list = res?.data?.content || res?.content || (Array.isArray(res?.data) ? res.data : []);
            setPatients(list);
        } catch (err) {
            setNotice(err?.message || 'Không thể tải danh sách bệnh nhân');
        } finally {
            setLoading(false);
        }
    }, [keyword]);

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
                openRegisterVisit(location.state.openVisitFor);
            }
        }, 0);
        return () => window.clearTimeout(timer);
    }, [location.state, openRegisterVisit]);

    const filteredPatients = patients;

    const openEditPatient = async (patient) => {
        try {
            const response = await patientApi.detail(patient.id);
            const detail = response?.data || response;
            setEditingPatient(detail);
            setEditForm({
                ...detail,
                version: detail.version ?? patient.version ?? 0,
            });
        } catch (err) {
            setNotice(err?.message || 'Không thể tải hồ sơ bệnh nhân');
        }
    };

    const handleSavePatient = async (event) => {
        event.preventDefault();
        setSavingPatient(true);
        try {
            const response = await patientApi.update(editingPatient.id, editForm);
            const updated = response?.data || response;
            setPatients((current) =>
                current.map((patient) => patient.id === updated.id ? updated : patient)
            );
            setEditingPatient(null);
            setEditForm(null);
            setNotice('Cập nhật hồ sơ bệnh nhân thành công.');
        } catch (err) {
            setNotice(err?.message || 'Không thể cập nhật hồ sơ bệnh nhân');
        } finally {
            setSavingPatient(false);
        }
    };

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
                                                    onClick={() => openRegisterVisit(patient)}
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
                                                <button
                                                    type="button"
                                                    title="Chỉnh sửa hồ sơ"
                                                    onClick={() => void openEditPatient(patient)}
                                                    className="rounded-lg border border-slate-200 p-1.5 text-slate-600 hover:bg-slate-50"
                                                >
                                                    <Pencil size={15} />
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

                                {editingPatient && editForm && (
                                    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                                        <form onSubmit={handleSavePatient} className="w-full max-w-2xl space-y-4 rounded-2xl bg-white p-6 shadow-xl">
                                            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                                                <h3 className="text-lg font-bold text-slate-800">Cập nhật hồ sơ bệnh nhân</h3>
                                                <button type="button" onClick={() => setEditingPatient(null)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                                                    <X size={18} />
                                                </button>
                                            </div>
                                            <div className="grid gap-4 sm:grid-cols-2">
                                                {[
                                                    ['hoten', 'Họ và tên'],
                                                    ['ngaysinh', 'Ngày sinh'],
                                                    ['cccdSo', 'Số CCCD'],
                                                    ['sodienthoai', 'Số điện thoại'],
                                                    ['diachi', 'Địa chỉ'],
                                                    ['dantoc', 'Dân tộc'],
                                                    ['dantocMa', 'Mã dân tộc'],
                                                    ['version', 'Phiên bản'],
                                                ].map(([field, label]) => (
                                                    <label key={field} className="text-sm font-semibold text-slate-700">
                                                        {label}
                                                        <input
                                                            type={field === 'ngaysinh' ? 'date' : field === 'version' ? 'number' : 'text'}
                                                            value={editForm[field] ?? ''}
                                                            onChange={(event) => setEditForm({ ...editForm, [field]: field === 'version' ? Number(event.target.value) : event.target.value })}
                                                            readOnly={field === 'version'}
                                                            required={['hoten', 'ngaysinh', 'diachi', 'dantoc', 'dantocMa'].includes(field)}
                                                            className={`${inputClass} mt-1`}
                                                        />
                                                    </label>
                                                ))}
                                                <label className="text-sm font-semibold text-slate-700">
                                                    Giới tính
                                                    <select
                                                        value={editForm.gioitinh ?? 3}
                                                        onChange={(event) => setEditForm({ ...editForm, gioitinh: Number(event.target.value) })}
                                                        className={`${inputClass} mt-1`}
                                                    >
                                                        <option value="1">Nam</option>
                                                        <option value="2">Nữ</option>
                                                        <option value="3">Khác</option>
                                                    </select>
                                                </label>
                                            </div>
                                            <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                                                <button type="button" onClick={() => setEditingPatient(null)} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">Hủy</button>
                                                <button type="submit" disabled={savingPatient} className="rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50">
                                                    {savingPatient ? 'Đang lưu...' : 'Lưu thay đổi'}
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                )}
        </div>
    );
}
