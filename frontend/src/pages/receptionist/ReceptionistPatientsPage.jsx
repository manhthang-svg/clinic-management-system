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
import defaultPatientApi from '../../apis/patientApi';
import { ROUTES } from '../../routes/routes';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';
import {
    DEFAULT_SEARCH_TYPE,
    MSG,
    SEARCH_TYPES,
    createPrefill,
    searchErrorMessage,
    validateKeyword,
} from '../../components/patients/patientSearch';

const inputClass =
    'w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

const MAX_RESULTS = 50;
const IDENTIFYING_TYPES = ['CITIZEN_ID', 'PASSPORT', 'PATIENT_CODE'];

/** UC-35 Search Patient (plus UC-38 update and the entry to UC-40). */
export default function ReceptionistPatientsPage({ patientApi = defaultPatientApi }) {
    const navigate = useNavigate();
    const location = useLocation();

    const [patients, setPatients] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [searchType, setSearchType] = useState(DEFAULT_SEARCH_TYPE);
    const [keyword, setKeyword] = useState('');
    const [keywordError, setKeywordError] = useState('');
    const [loadError, setLoadError] = useState('');
    const [lastSearch, setLastSearch] = useState(null);
    const [notice, setNotice] = useState('');
    const [editingPatient, setEditingPatient] = useState(null);
    const [editForm, setEditForm] = useState(null);
    const [savingPatient, setSavingPatient] = useState(false);

    // UC-40: đăng ký khám mở trang riêng thay vì popup.
    const openRegisterVisit = useCallback(
        (patient) => navigate(generatePath(ROUTES.RECEPTIONIST.VISIT_REGISTER, { id: patient.id })),
        [navigate]
    );

    // Without a search the page lists the latest patients; a search sends its type (UC-35).
    const fetchPatients = useCallback(async (search) => {
        setLoading(true);
        setLoadError('');
        try {
            const res = await patientApi.list({ size: MAX_RESULTS, ...(search ?? {}) });
            const page = res?.data ?? res;
            const list = page?.content || (Array.isArray(page) ? page : []);
            // AF-04: one profile per identification number (BR-12), so a single match opens UC-36 directly.
            if (search && IDENTIFYING_TYPES.includes(search.type) && list.length === 1
                && (page?.totalElements ?? 1) === 1) {
                navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: list[0].id }));
                return;
            }
            setPatients(list);
            setTotalElements(page?.totalElements ?? list.length);
            setLastSearch(search);
        } catch (err) {
            const message = searchErrorMessage(err);
            if (err?.errorResponse?.details?.keyword) setKeywordError(message);
            else setLoadError(message);
        } finally {
            setLoading(false);
        }
    }, [navigate, patientApi]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPatients(null);
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPatients]);

    const handleSearch = (event) => {
        event.preventDefault();
        const error = validateKeyword(searchType, keyword);
        setKeywordError(error);
        if (error) return;
        void fetchPatients({ type: searchType, keyword: keyword.trim() });
    };

    const clearSearch = () => {
        setKeyword('');
        setKeywordError('');
        void fetchPatients(null);
    };

    const createFromSearch = () => navigate(ROUTES.RECEPTIONIST.PATIENT_CREATE, {
        state: { prefill: lastSearch ? createPrefill(lastSearch.type, lastSearch.keyword) : {} },
    });

    const selectedType = SEARCH_TYPES.find((item) => item.value === searchType);

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

    const openEditPatient = useCallback(async (patient) => {
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
    }, [patientApi]);

    // UC-36 "Cập nhật thông tin" opens the update form of that patient (UC-38).
    const editPatientId = location.state?.editPatientId;
    useEffect(() => {
        if (!editPatientId) return undefined;
        const timer = window.setTimeout(() => void openEditPatient({ id: editPatientId }), 0);
        return () => window.clearTimeout(timer);
    }, [editPatientId, openEditPatient]);

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
                        onClick={() => void fetchPatients(lastSearch)}
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
                <form onSubmit={handleSearch} noValidate className="grid grid-cols-1 gap-3 md:grid-cols-[200px_1fr_auto]">
                    <div>
                        <label htmlFor="patient-search-type" className="text-xs font-semibold text-slate-600">Tìm theo</label>
                        <select
                            id="patient-search-type"
                            value={searchType}
                            onChange={(event) => {
                                setSearchType(event.target.value);
                                setKeywordError('');
                            }}
                            className={`${inputClass} mt-1`}
                        >
                            {SEARCH_TYPES.map((item) => (
                                <option key={item.value} value={item.value}>{item.label}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label htmlFor="patient-search-keyword" className="text-xs font-semibold text-slate-600">Từ khóa</label>
                        <span className="relative mt-1 block">
                            <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                            <input
                                id="patient-search-keyword"
                                value={keyword}
                                onChange={(event) => setKeyword(event.target.value)}
                                placeholder={selectedType?.placeholder}
                                aria-invalid={keywordError ? 'true' : undefined}
                                aria-describedby={keywordError ? 'patient-search-error' : undefined}
                                className={`${inputClass} pl-10 ${keywordError ? 'border-red-400' : ''}`}
                            />
                        </span>
                        {keywordError && (
                            <span id="patient-search-error" role="alert" className="mt-1 block text-xs font-medium text-red-600">
                                {keywordError}
                            </span>
                        )}
                    </div>

                    <div className="flex items-end gap-2">
                        <button
                            type="submit"
                            disabled={loading}
                            className="rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
                        >
                            {loading ? 'Đang tìm…' : 'Tìm kiếm'}
                        </button>
                        {lastSearch && (
                            <button
                                type="button"
                                onClick={clearSearch}
                                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                            >
                                Xóa tìm kiếm
                            </button>
                        )}
                    </div>
                </form>
            </section>

            {loadError && (
                <div role="alert" className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                    <AlertCircle size={18} className="shrink-0" />
                    <span>{loadError}</span>
                </div>
            )}

            <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-100 p-5">
                    <div>
                        <h2 className="font-bold text-slate-800">{lastSearch ? 'Kết quả tìm kiếm' : 'Danh sách bệnh nhân'}</h2>
                        <p className="mt-1 text-sm text-slate-500">Có {filteredPatients.length} bệnh nhân</p>
                    </div>
                </div>

                {lastSearch && totalElements > filteredPatients.length && (
                    <p role="status" className="border-b border-amber-100 bg-amber-50 px-5 py-3 text-sm text-amber-800">
                        {MSG.TOO_MANY}
                    </p>
                )}

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[1100px] text-left">
                        <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
                            <tr>
                                {['Mã BN', 'Họ và tên', 'Ngày sinh', 'Giới tính', 'CCCD / Hộ chiếu', 'Số điện thoại', 'Lần khám gần nhất', 'Lượt khám đang mở', 'Thao tác'].map((heading) => (
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
                                        <td className="px-5 py-4 text-sm font-mono text-slate-600">{patient.cccdSo || patient.hochieuSo || '—'}</td>
                                        <td className="px-5 py-4 text-sm text-slate-600">{phone}</td>
                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {patient.lastVisitAt ? new Date(patient.lastVisitAt).toLocaleDateString('vi-VN') : '—'}
                                        </td>
                                        <td className="px-5 py-4 text-sm text-slate-600">
                                            {patient.activeVisit ? (
                                                <span className="inline-flex flex-col">
                                                    <span className="font-semibold text-amber-700">
                                                        {VISIT_STATUS_LABELS[patient.activeVisit.status] ?? patient.activeVisit.status}
                                                    </span>
                                                    <span className="text-xs text-slate-500">
                                                        {patient.activeVisit.registrationCode} · {patient.activeVisit.roomName}
                                                    </span>
                                                </span>
                                            ) : '—'}
                                        </td>
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
                                                    onClick={() => navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: patient.id }))}
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
                                    <td colSpan={9} className="px-5 py-12 text-center text-sm text-slate-500">
                                        {loading ? 'Đang tải danh sách bệnh nhân…' : (
                                            <span className="inline-flex flex-col items-center gap-3">
                                                <span>{MSG.NO_RESULT}</span>
                                                {lastSearch && (
                                                    <button
                                                        type="button"
                                                        onClick={createFromSearch}
                                                        className="inline-flex items-center gap-2 rounded-xl bg-[#0F9D9A] px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
                                                    >
                                                        <UserPlus size={16} />
                                                        Tạo hồ sơ bệnh nhân mới
                                                    </button>
                                                )}
                                            </span>
                                        )}
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
