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
    ChevronsLeft,
    ChevronsRight,
} from 'lucide-react';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
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

const PAGE_SIZE = 20;
const PAGE_BUTTON_CLASS =
    'flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300';

// UC-35: trang tim kiem benh nhan (tu day mo ho so, sua ho so, dang ky kham)
export default function ReceptionistPatientsPage({ patientApi = defaultPatientApi }) {
    const navigate = useNavigate();
    const location = useLocation();

    const [patients, setPatients] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [pageNumber, setPageNumber] = useState(0);
    const [loading, setLoading] = useState(false);
    const [searchType, setSearchType] = useState(DEFAULT_SEARCH_TYPE);
    const [keyword, setKeyword] = useState('');
    const [keywordError, setKeywordError] = useState('');
    const [loadError, setLoadError] = useState('');
    const [lastSearch, setLastSearch] = useState(null);
    const [notice, setNotice] = useState('');

    // UC-40: mo trang dang ky kham rieng
    const openRegisterVisit = useCallback(
        (patient) => navigate(generatePath(ROUTES.RECEPTIONIST.VISIT_REGISTER, { id: patient.id })),
        [navigate]
    );

    // xoa ket qua dang hien
    const clearResults = useCallback(() => {
        setPatients([]);
        setTotalElements(0);
        setPageNumber(0);
        setLastSearch(null);
    }, []);

    // tai 1 trang benh nhan: khong tim thi lay het, co tim thi gui kem loai tim
    const fetchPatients = useCallback(async (search, pageToLoad = 0) => {
        setLoading(true);
        setLoadError('');
        try {
            const res = await patientApi.list({ page: pageToLoad, size: PAGE_SIZE, ...(search ?? {}) });
            const page = res?.data ?? res;
            const list = page?.content || (Array.isArray(page) ? page : []);
            // chi 1 ket qua van hien trong danh sach, khong tu mo ho so
            setPatients(list);
            setTotalElements(page?.totalElements ?? list.length);
            setPageNumber(pageToLoad);
            setLastSearch(search);
        } catch (err) {
            const message = searchErrorMessage(err);
            if (err?.errorResponse?.details?.keyword) {
                setKeywordError(message);
                clearResults();
            } else {
                setLoadError(message);
            }
        } finally {
            setLoading(false);
        }
    }, [clearResults, patientApi]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPatients(null);
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPatients]);

    // bam Tim: kiem tra tu khoa roi tai trang dau
    const handleSearch = (event) => {
        event.preventDefault();
        const error = validateKeyword(searchType, keyword);
        setKeywordError(error);
        if (error) {
            // tu khoa sai thi xoa ket qua cu
            clearResults();
            return;
        }
        void fetchPatients({ type: searchType, keyword: keyword.trim() });
    };

    // xoa tu khoa, hien lai tat ca benh nhan
    const clearSearch = () => {
        setKeyword('');
        setKeywordError('');
        void fetchPatients(null);
    };

    const openPatientDetail = (patient) =>
        navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: patient.id }));

    // tim khong ra: mo form tao ho so, dien san tu khoa
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
    const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
    const firstRow = totalElements === 0 ? 0 : pageNumber * PAGE_SIZE + 1;
    const lastRow = pageNumber * PAGE_SIZE + filteredPatients.length;
    const goToPage = (target) => void fetchPatients(lastSearch, target);

    // UC-38: mo trang sua ho so
    const openEditPatient = (patient) =>
        navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_EDIT, { id: patient.id }));

    return (
        <div className="mx-auto w-full max-w-[1600px] space-y-6">
            <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <AccountPageHeader
                    onHome={() => navigate(ROUTES.RECEPTIONIST.OVERVIEW)}
                    title="Quản lý & Tiếp đón bệnh nhân"
                    description="Tìm kiếm hồ sơ bệnh nhân, đăng ký lượt khám và chuyển thu ngân thanh toán phí khám lần 1."
                    crumbs={[{ label: 'Quản lý bệnh nhân' }]}
                />

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
                        <p className="mt-1 text-sm text-slate-500">Có {totalElements} bệnh nhân</p>
                    </div>
                </div>

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
                                    <tr
                                        key={patient.id}
                                        onClick={() => openPatientDetail(patient)}
                                        title="Xem hồ sơ bệnh nhân"
                                        className="cursor-pointer transition hover:bg-teal-50/40"
                                    >
                                        <td className="px-5 py-4 text-sm font-semibold font-mono text-teal-700">{code}</td>
                                        <td className="px-5 py-4 text-sm font-semibold text-slate-800">
                                            <button
                                                type="button"
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    openPatientDetail(patient);
                                                }}
                                                className="text-left hover:text-teal-700 hover:underline"
                                            >
                                                {name}
                                            </button>
                                        </td>
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
                                                    onClick={(event) => {
                                                        event.stopPropagation();
                                                        openRegisterVisit(patient);
                                                    }}
                                                    className="inline-flex items-center gap-1.5 rounded-lg bg-[#0F9D9A] px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-teal-700"
                                                    title="Đăng ký khám cho bệnh nhân"
                                                >
                                                    <CalendarPlus size={14} />
                                                    Đăng ký khám
                                                </button>
                                                <button
                                                    type="button"
                                                    title="Xem hồ sơ"
                                                    onClick={(event) => {
                                                        event.stopPropagation();
                                                        openPatientDetail(patient);
                                                    }}
                                                    className="rounded-lg border border-slate-200 p-1.5 text-slate-600 hover:bg-slate-50"
                                                >
                                                    <Eye size={15} />
                                                </button>
                                                <button
                                                    type="button"
                                                    title="Chỉnh sửa hồ sơ"
                                                    onClick={(event) => {
                                                        event.stopPropagation();
                                                        void openEditPatient(patient);
                                                    }}
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
                                        {loading ? 'Đang tải danh sách bệnh nhân…' : keywordError ? (
                                            'Sửa từ khóa ở trên để tìm kiếm.'
                                        ) : (
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

                {totalElements > 0 && (
                    <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-100 px-5 py-4 text-sm text-slate-500 sm:flex-row">
                        <span>Hiển thị {firstRow}–{lastRow} trên tổng số {totalElements} bệnh nhân</span>
                        <nav aria-label="Phân trang" className="flex items-center gap-1.5">
                            <button
                                type="button"
                                aria-label="Trang trước"
                                disabled={loading || pageNumber === 0}
                                onClick={() => goToPage(pageNumber - 1)}
                                className={PAGE_BUTTON_CLASS}
                            >
                                <ChevronsLeft size={14} />
                            </button>
                            <span className="px-2">Trang {pageNumber + 1}/{totalPages}</span>
                            <button
                                type="button"
                                aria-label="Trang sau"
                                disabled={loading || pageNumber + 1 >= totalPages}
                                onClick={() => goToPage(pageNumber + 1)}
                                className={PAGE_BUTTON_CLASS}
                            >
                                <ChevronsRight size={14} />
                            </button>
                        </nav>
                    </div>
                )}
            </section>

        </div>
    );
}
