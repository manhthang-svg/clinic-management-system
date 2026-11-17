import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { FolderHeart, RefreshCw, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { ApiError } from '../../../apis/apiError';
import { MedicalRecordsFilter } from './MedicalRecordsFilter';
import { PatientListTable } from './PatientListTable';
import { PatientVisitListTable } from './PatientVisitListTable';
import { MedicalRecordDetailModal } from './MedicalRecordDetailModal';

const INITIAL_FILTERS = {
  keyword: '',
  from: '',
  to: '',
  roomId: 'ALL',
  icd10: '',
  status: 'ALL',
};

function formatErrorMessage(error) {
  if (!(error instanceof ApiError)) {
    return error?.message || 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.';
  }
  if (error.status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.';
  }
  if (error.errorResponse?.code === 'REQUEST_VALIDATION_FAILED' || error.errorResponse?.code === 'REQUEST_PARAMETER_INVALID') {
    return error.errorResponse?.message || 'Ngày bắt đầu không được sau ngày kết thúc.';
  }
  if (error.status === undefined) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  }
  return error.message || 'Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.';
}

/**
 * Screen #39 – HỒ SƠ BỆNH ÁN / LỊCH SỬ HỒ SƠ BỆNH ÁN (Medical Record History)
 * Luồng 2 cấp độ: Cấp 1 (Tìm bệnh nhân) -> Cấp 2 (Danh sách lượt khám) -> UC-52 (Chi tiết bệnh án).
 */
export function MedicalRecordsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const shortcutPatientId = searchParams.get('patientId');

  // Bộ lọc
  const [filters, setFilters] = useState(INITIAL_FILTERS);

  // Dữ liệu Cấp 1 (Danh sách bệnh nhân)
  const [patients, setPatients] = useState([]);
  const [pageInfo, setPageInfo] = useState({ number: 0, size: 20, totalElements: 0, totalPages: 1 });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [loadingPatients, setLoadingPatients] = useState(false);
  const [listError, setListError] = useState(null);

  // Dữ liệu Cấp 2 (Bệnh nhân được chọn & Danh sách lượt khám)
  const [selectedPatient, setSelectedPatient] = useState(null);
  const [patientVisits, setPatientVisits] = useState([]);
  const [loadingVisits, setLoadingVisits] = useState(false);
  const [visitsError, setVisitsError] = useState(null);

  // Chi tiết bệnh án Modal (UC-52)
  const [activeVisitModal, setActiveVisitModal] = useState(null);

  // Handler thay đổi từng trường filter
  const handleChangeFilter = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
  };

  // Tải danh sách bệnh nhân (Cấp 1)
  const loadPatients = useCallback(async (pageNum = page, pageSize = size) => {
    setLoadingPatients(true);
    setListError(null);
    try {
      const res = await medicalRecordApi.searchPatients({
        from: filters.from || undefined,
        to: filters.to || undefined,
        status: filters.status,
        roomId: filters.roomId,
        icd10: filters.icd10 || undefined,
        keyword: filters.keyword || undefined,
        page: pageNum,
        size: pageSize,
      });

      if (res) {
        setPatients(res.content ?? []);
        setPageInfo(res.page ?? { number: pageNum, size: pageSize, totalElements: (res.content ?? []).length, totalPages: 1 });
      }
    } catch (err) {
      console.error('Lỗi tải danh sách hồ sơ bệnh nhân:', err);
      const msg = formatErrorMessage(err);
      setListError(msg);
      toast.error(msg);
    } finally {
      setLoadingPatients(false);
    }
  }, [filters, page, size]);

  // Tải danh sách lượt khám khi chọn 1 bệnh nhân (Cấp 2)
  const loadVisitsForPatient = useCallback(async (patientId, patientData = null) => {
    setLoadingVisits(true);
    setVisitsError(null);
    try {
      const visits = await medicalRecordApi.getPatientVisits(patientId);
      setPatientVisits(Array.isArray(visits) ? visits : []);
      if (patientData) {
        setSelectedPatient(patientData);
      } else {
        // Nếu mở shortcut AF-01 mà chưa có patient object
        setSelectedPatient({
          patientId,
          fullName: 'Bệnh nhân đang khám',
          patientCode: 'BN...',
        });
      }
    } catch (err) {
      console.error('Lỗi tải lượt khám của bệnh nhân:', err);
      const msg = formatErrorMessage(err);
      setVisitsError(msg);
      toast.error(msg);
    } finally {
      setLoadingVisits(false);
    }
  }, []);

  // Effect ban đầu: Tự động tải danh sách hoặc xử lý shortcut AF-01
  useEffect(() => {
    if (shortcutPatientId) {
      loadVisitsForPatient(shortcutPatientId);
    } else {
      loadPatients(0, size);
    }
  }, [shortcutPatientId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Nút tìm kiếm
  const handleSearch = () => {
    setPage(0);
    loadPatients(0, size);
  };

  // Nút xóa bộ lọc
  const handleReset = () => {
    setFilters(INITIAL_FILTERS);
    setPage(0);
    // Reload với bộ lọc trắng
    setLoadingPatients(true);
    medicalRecordApi
      .searchPatients({ page: 0, size })
      .then((res) => {
        setPatients(res.content ?? []);
        setPageInfo(res.page ?? { number: 0, size, totalElements: (res.content ?? []).length, totalPages: 1 });
      })
      .catch((err) => {
        const msg = formatErrorMessage(err);
        setListError(msg);
        toast.error(msg);
      })
      .finally(() => setLoadingPatients(false));
  };

  // Chọn 1 bệnh nhân từ Cấp 1 để mở Cấp 2
  const handleSelectPatient = (patient) => {
    loadVisitsForPatient(patient.patientId, patient);
  };

  // Quay lại Cấp 1 từ Cấp 2
  const handleBackToPatients = () => {
    setSelectedPatient(null);
    setPatientVisits([]);
    if (shortcutPatientId) {
      // Xóa param shortcut khỏi URL để ở lại màn hình Cấp 1
      setSearchParams({});
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Title & Header */}
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

        {/* Nút tải lại */}
        <button
          type="button"
          onClick={() => {
            if (selectedPatient) {
              loadVisitsForPatient(selectedPatient.patientId, selectedPatient);
            } else {
              loadPatients(page, size);
            }
          }}
          disabled={loadingPatients || loadingVisits}
          className="inline-flex items-center gap-2 px-3 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl shadow-2xs cursor-pointer transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${(loadingPatients || loadingVisits) ? 'animate-spin' : ''}`} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* Thông báo lỗi tổng nếu có */}
      {(listError || visitsError) && (
        <div className="flex items-center gap-2 p-4 text-sm font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-2xl">
          <AlertCircle className="w-5 h-5 shrink-0 text-rose-600" />
          <span>{listError || visitsError}</span>
        </div>
      )}

      {/* LUỒNG 2 CẤP ĐỘ: CẤP 1 (Danh sách bệnh nhân) hoặc CẤP 2 (Danh sách lượt khám) */}
      {!selectedPatient ? (
        <>
          {/* Khu vực Tìm kiếm & Bộ lọc (A) */}
          <MedicalRecordsFilter
            filters={filters}
            onChangeFilter={handleChangeFilter}
            onSearch={handleSearch}
            onReset={handleReset}
            loading={loadingPatients}
          />

          {/* Cấp 1: Bảng Danh sách Bệnh nhân khớp điều kiện (B) */}
          <PatientListTable
            patients={patients}
            pageInfo={pageInfo}
            page={page}
            size={size}
            onChangePage={(newPage) => {
              setPage(newPage);
              loadPatients(newPage, size);
            }}
            onChangeSize={(newSize) => {
              setSize(newSize);
              setPage(0);
              loadPatients(0, newSize);
            }}
            onSelectPatient={handleSelectPatient}
            loading={loadingPatients}
          />
        </>
      ) : (
        /* Cấp 2: Danh sách Lượt khám của Bệnh nhân được chọn (C) */
        <PatientVisitListTable
          patient={selectedPatient}
          visits={patientVisits}
          loading={loadingVisits}
          onBack={handleBackToPatients}
          onViewDetail={(visit) => setActiveVisitModal(visit)}
        />
      )}

      {/* Modal Chi tiết Hồ sơ Bệnh án (UC-52) */}
      {activeVisitModal && (
        <MedicalRecordDetailModal
          visitId={activeVisitModal.visitId}
          recordId={activeVisitModal.medicalRecordId}
          onClose={() => setActiveVisitModal(null)}
        />
      )}
    </div>
  );
}

export default MedicalRecordsPage;

