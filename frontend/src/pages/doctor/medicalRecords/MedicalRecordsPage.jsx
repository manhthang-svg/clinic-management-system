import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { FolderHeart, RefreshCw, AlertCircle } from 'lucide-react';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { ROUTES } from '../../../routes/routes';
import { MedicalRecordsFilter } from './MedicalRecordsFilter';
import { PatientListTable } from './PatientListTable';
import { PatientVisitListTable } from './PatientVisitListTable';
import { recordErrorMessage } from './medicalRecordView';

const INITIAL_FILTERS = {
  keyword: '',
  from: '',
  to: '',
  roomId: 'ALL',
  icd10: '',
  status: 'ALL',
};

const EMPTY_PAGE = { number: 0, size: 20, totalElements: 0, totalPages: 0 };

function searchParamsOf({ filters, page, size }) {
  return {
    keyword: filters.keyword.trim() || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    roomId: filters.roomId,
    icd10: filters.icd10.trim() || undefined,
    status: filters.status,
    page,
    size,
  };
}

/**
 * UC-51 View List Medical Record: cấp 1 tìm bệnh nhân, cấp 2 danh sách lượt khám, chọn lượt thì sang UC-52.
 * Bệnh nhân đang chọn nằm trong `?patientId=` (UUID, BR-51-03), nên AF-01 và nút Quay lại dùng chung một đường.
 * Bộ lọc không bao giờ được đưa vào URL.
 */
export function MedicalRecordsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedPatientId = searchParams.get('patientId');

  // Cấp 1: bộ lọc đang nhập và truy vấn đã gửi. Kết quả nhớ truy vấn của nó, nên "đang tải" = chưa khớp.
  const [draft, setDraft] = useState(INITIAL_FILTERS);
  const [query, setQuery] = useState({ filters: INITIAL_FILTERS, page: 0, size: 20, run: 0 });
  const [list, setList] = useState({ query: null, patients: [], pageInfo: EMPTY_PAGE, error: null });

  // Cấp 2: kết quả nhớ khóa (bệnh nhân + lần tải lại) của nó.
  const [visitsReload, setVisitsReload] = useState(0);
  const visitsKey = selectedPatientId ? `${selectedPatientId}:${visitsReload}` : null;
  const [history, setHistory] = useState({ key: null, data: null, error: null });

  useEffect(() => {
    if (selectedPatientId) return undefined;
    const controller = new AbortController();
    medicalRecordApi
      .searchPatients(searchParamsOf(query), controller.signal)
      .then((res) => {
        setList({ query, patients: res?.content ?? [], pageInfo: res?.page ?? EMPTY_PAGE, error: null });
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        setList({ query, patients: [], pageInfo: EMPTY_PAGE, error: recordErrorMessage(err) });
      });
    return () => controller.abort();
  }, [query, selectedPatientId]);

  useEffect(() => {
    if (!selectedPatientId) return undefined;
    const controller = new AbortController();
    medicalRecordApi
      .getPatientVisits(selectedPatientId, controller.signal)
      .then((data) => setHistory({ key: visitsKey, data, error: null }))
      .catch((err) => {
        if (controller.signal.aborted) return;
        setHistory({ key: visitsKey, data: null, error: recordErrorMessage(err) });
      });
    return () => controller.abort();
  }, [selectedPatientId, visitsKey]);

  const loadingPatients = !selectedPatientId && list.query !== query;
  const loadingVisits = Boolean(visitsKey) && history.key !== visitsKey;
  const currentHistory = history.key === visitsKey ? history : { data: null, error: null };
  const error = selectedPatientId ? currentHistory.error : list.error;

  const runSearch = (filters) => {
    setQuery((current) => ({ filters, page: 0, size: current.size, run: current.run + 1 }));
  };

  const handleReset = () => {
    setDraft(INITIAL_FILTERS);
    runSearch(INITIAL_FILTERS);
  };

  const handleRefresh = () => {
    if (selectedPatientId) {
      setVisitsReload((n) => n + 1);
    } else {
      setQuery((current) => ({ ...current, run: current.run + 1 }));
    }
  };

  const handleViewDetail = (visit) => {
    navigate(`${ROUTES.DOCTOR.MEDICAL_RECORDS}/visits/${encodeURIComponent(visit.visitId)}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a]">
              <FolderHeart className="w-5 h-5" />
            </div>
            <h1 className="text-2xl font-extrabold text-[#1b2533] tracking-tight">
              Hồ sơ bệnh án / Lịch sử hồ sơ bệnh án
            </h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Tra cứu hồ sơ bệnh án và lịch sử các lượt khám của tất cả bệnh nhân trong cơ sở.
          </p>
        </div>

        <button
          type="button"
          onClick={handleRefresh}
          disabled={loadingPatients || loadingVisits}
          className="inline-flex items-center gap-2 px-3 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl shadow-2xs cursor-pointer transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${(loadingPatients || loadingVisits) ? 'animate-spin' : ''}`} />
          <span>{loadingPatients || loadingVisits ? 'Đang tải…' : 'Làm mới'}</span>
        </button>
      </div>

      {error && (
        <div
          role="alert"
          className="flex items-center gap-2 p-4 text-sm font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-2xl"
        >
          <AlertCircle className="w-5 h-5 shrink-0 text-rose-600" />
          <span>{error}</span>
        </div>
      )}

      {!selectedPatientId ? (
        <>
          <MedicalRecordsFilter
            filters={draft}
            onChangeFilter={(field, value) => setDraft((prev) => ({ ...prev, [field]: value }))}
            onSearch={() => runSearch(draft)}
            onReset={handleReset}
            loading={loadingPatients}
          />

          <PatientListTable
            patients={list.patients}
            pageInfo={list.pageInfo}
            page={query.page}
            size={query.size}
            onChangePage={(page) => setQuery((current) => ({ ...current, page }))}
            onChangeSize={(size) => setQuery((current) => ({ ...current, size, page: 0 }))}
            onSelectPatient={(patient) => setSearchParams({ patientId: patient.patientId })}
            loading={loadingPatients}
          />
        </>
      ) : (
        <PatientVisitListTable
          patient={currentHistory.data?.patient}
          visits={currentHistory.data?.visits ?? []}
          loading={loadingVisits}
          onBack={() => setSearchParams({})}
          onViewDetail={handleViewDetail}
        />
      )}
    </div>
  );
}

export default MedicalRecordsPage;
