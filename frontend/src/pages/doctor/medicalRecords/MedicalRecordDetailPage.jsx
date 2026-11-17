import { useEffect, useState, useId } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Calendar,
  Clock,
  Download,
  Edit,
  ExternalLink,
  FileCheck,
  FileText,
  Heart,
  ImageIcon,
  Info,
  MapPin,
  Pill,
  Printer,
  RefreshCw,
  ShieldAlert,
  Stethoscope,
  Tag,
  User,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { ApiError } from '../../../apis/apiError';
import { ROUTES } from '../../../routes/routes';

/**
 * Màn hình Xem chi tiết hồ sơ bệnh án (View Medical Record Detail)
 * Đáp ứng đầy đủ quy tắc chỉ đọc, ngoại lệ chỉnh sửa lượt khám dở dang,
 * thanh timeline lượt khám, xem tệp kết quả CLS và xuất hồ sơ bệnh án.
 */
export function MedicalRecordDetailPage({ isVisitRoute = false }) {
  const { id, visitId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [recordDetail, setRecordDetail] = useState(null);

  // Modal xem ảnh kết quả CLS phóng to
  const [previewImage, setPreviewImage] = useState(null);

  // Modal xuất hồ sơ bệnh án (UC-53)
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportScope, setExportScope] = useState('CURRENT_VISIT');
  const [isExporting, setIsExporting] = useState(false);

  // Target ID xác định theo route
  const targetId = isVisitRoute ? visitId : id;

  const loadDetail = async (identifier, isByVisit) => {
    if (!identifier) return;
    setLoading(true);
    setError(null);
    try {
      const data = isByVisit
        ? await medicalRecordApi.getMedicalRecordByVisitId(identifier)
        : await medicalRecordApi.getMedicalRecordDetail(identifier);
      setRecordDetail(data);
    } catch (err) {
      console.error('Lỗi tải chi tiết hồ sơ bệnh án:', err);
      if (err instanceof ApiError) {
        if (err.status === 401) {
          toast.error('Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.');
          navigate('/login');
          return;
        }
        if (err.status === 403) {
          setError('Bạn không có quyền thực hiện thao tác này.');
          return;
        }
        if (err.status === 404) {
          setError('Hồ sơ bệnh án không tồn tại hoặc đã bị xóa.');
          return;
        }
      }
      setError('Đã xảy ra lỗi khi tải hồ sơ bệnh án. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDetail(targetId, isVisitRoute);
  }, [targetId, isVisitRoute]);

  // Chuyển sang lượt khám khác trên Timeline
  const handleSelectTimelineVisit = (timelineVisitId) => {
    if (!timelineVisitId || timelineVisitId === recordDetail?.visitId) return;
    navigate(`/doctor/medical-records/visits/${encodeURIComponent(timelineVisitId)}`);
  };

  // Mở tệp ảnh kết quả cận lâm sàng và ghi nhật ký kiểm toán (I.3.3 / BR-42)
  const handleOpenResultImage = async (image) => {
    setPreviewImage(image);
    if (image?.imageId) {
      try {
        await medicalRecordApi.recordFileAccessLog(image.imageId);
      } catch (err) {
        console.error('Lỗi ghi nhật ký truy cập tệp:', err);
      }
    }
  };

  // Xuất hồ sơ bệnh án ra PDF / In (UC-53)
  const handleExportPdf = async () => {
    if (!recordDetail?.medicalRecordId) return;
    setIsExporting(true);
    try {
      await medicalRecordApi.recordExportLog(recordDetail.medicalRecordId, exportScope);
      toast.success('Đã xuất hồ sơ bệnh án thành công.');
      setExportModalOpen(false);
      // Mở hộp thoại in trình duyệt chuẩn
      setTimeout(() => {
        window.print();
      }, 300);
    } catch (err) {
      console.error('Lỗi xuất bệnh án:', err);
      toast.error('Không thể xuất hồ sơ bệnh án. Vui lòng thử lại sau.');
    } finally {
      setIsExporting(false);
    }
  };

  // In phiếu với nhãn bản sao
  const handleReprint = (type) => {
    toast.info(`Chuẩn bị in lại ${type} (Bản sao)...`);
    setTimeout(() => {
      window.print();
    }, 300);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[500px] p-8 text-slate-500">
        <RefreshCw className="w-8 h-8 animate-spin text-[#127e7a] mb-3" />
        <p className="text-sm font-semibold">Đang tải chi tiết hồ sơ bệnh án...</p>
      </div>
    );
  }

  if (error || !recordDetail) {
    return (
      <div className="max-w-4xl mx-auto p-6 mt-8">
        <div className="bg-white rounded-2xl border border-rose-200 p-8 text-center space-y-4 shadow-sm">
          <AlertCircle className="w-12 h-12 text-rose-500 mx-auto" />
          <h2 className="text-lg font-bold text-slate-900">Không thể hiển thị hồ sơ</h2>
          <p className="text-sm text-slate-600 max-w-md mx-auto">{error || 'Không tìm thấy hồ sơ bệnh án yêu cầu.'}</p>
          <div className="pt-2">
            <button
              type="button"
              onClick={() => navigate(ROUTES.DOCTOR.MEDICAL_RECORDS)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-[#127e7a] text-white text-sm font-semibold rounded-xl hover:bg-[#0e6360] transition-colors cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Quay lại danh sách hồ sơ</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  const {
    recordNumber,
    storageNumber,
    patientCode,
    patientName,
    age,
    gender,
    address,
    phoneNumber,
    citizenId,
    drugAllergies = [],
    medicalHistorySummary,
    visitCode,
    visitDate,
    registeredAt,
    roomName,
    doctorName,
    visitType,
    insuranceCardNumber,
    isPriority,
    priorityReason,
    visitStatus,
    isAbandoned,
    isEditable,
    reasonForVisit,
    transfers = [],
    vitalSigns,
    clinicalExam,
    paraclinicalOrders = [],
    conclusion,
    prescription,
    followUpAppointment,
    visitTimeline = [],
  } = recordDetail;

  return (
    <div className="max-w-7xl mx-auto p-4 sm:p-6 space-y-6">
      {/* 1. THANH ĐIỀU HƯỚNG & HÀNH ĐỘNG ĐẦU TRANG */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-4 print:hidden">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate(ROUTES.DOCTOR.MEDICAL_RECORDS)}
            className="flex items-center justify-center w-9 h-9 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 transition-colors cursor-pointer"
            title="Quay lại danh sách hồ sơ bệnh án"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">Chi tiết Hồ sơ Bệnh án</h1>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-700 border border-slate-200">
                {recordNumber || 'Chưa cấp số'}
              </span>
              {storageNumber && (
                <span className="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                  Lưu trữ: {storageNumber}
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Lượt khám: <strong>{visitCode || '—'}</strong>
              {registeredAt && ` • ${new Date(registeredAt).toLocaleString('vi-VN')}`}
            </p>
          </div>
        </div>

        {/* Các nút thao tác */}
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Nút Chỉnh sửa (AF-01: Chỉ khi lượt khám dở dang của chính bác sĩ) */}
          {isEditable && (
            <button
              type="button"
              onClick={() => navigate(`/doctor/clinical-examination/${recordDetail.visitId}`)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 rounded-xl transition-all shadow-sm cursor-pointer"
            >
              <Edit className="w-3.5 h-3.5" />
              <span>Chỉnh sửa ca khám hiện tại</span>
            </button>
          )}

          {/* In phiếu (Reprint bản sao) */}
          <div className="relative inline-block text-left group">
            <button
              type="button"
              onClick={() => handleReprint('phiếu khám')}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl transition-all cursor-pointer shadow-2xs"
            >
              <Printer className="w-3.5 h-3.5 text-slate-500" />
              <span>In phiếu (Bản sao)</span>
            </button>
          </div>

          {/* Nút Xuất hồ sơ bệnh án (UC-53) */}
          <button
            type="button"
            onClick={() => setExportModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all shadow-sm cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Xuất hồ sơ bệnh án</span>
          </button>
        </div>
      </div>

      {/* 2. KHU VỰC A: THÔNG TIN TỔNG QUAN BỆNH NHÂN (PATIENT HEADER) */}
      <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-3">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a] shrink-0 font-bold">
              <User className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-black text-slate-900">{patientName || 'Chưa có tên'}</h2>
                <span className="text-xs font-extrabold text-[#127e7a] bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200">
                  {patientCode}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 mt-0.5">
                <span>
                  {gender} • {age !== null ? `${age} tuổi` : '—'}
                </span>
                {citizenId && <span>• CCCD: {citizenId}</span>}
                {phoneNumber && <span>• SĐT: {phoneNumber}</span>}
                {address && <span>• Đ/c: {address}</span>}
              </div>
            </div>
          </div>

          {/* Nhãn cảnh báo Dị ứng thuốc nổi bật (Bắt buộc theo đặc tả UC-52) */}
          <div>
            {drugAllergies && drugAllergies.length > 0 ? (
              <div className="inline-flex items-center gap-2 px-3.5 py-2 bg-rose-50 border border-rose-300 rounded-xl text-rose-800 text-xs font-bold shadow-2xs">
                <ShieldAlert className="w-4 h-4 text-rose-600 shrink-0" />
                <span>Dị ứng: {drugAllergies.join(', ')}</span>
              </div>
            ) : (
              <span className="text-xs text-slate-400 font-medium px-3 py-1 bg-slate-50 rounded-lg border border-slate-100">
                Chưa ghi nhận dị ứng thuốc
              </span>
            )}
          </div>
        </div>

        {/* Tiền sử bệnh */}
        {medicalHistorySummary && (
          <div className="text-xs bg-slate-50 border border-slate-200/80 rounded-xl p-3 text-slate-700 flex items-start gap-2">
            <Info className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
            <div>
              <strong className="text-slate-900">Tiền sử bệnh & yếu tố nguy cơ:</strong> {medicalHistorySummary}
            </div>
          </div>
        )}
      </section>

      {/* 3. KHU VỰC B: THANH ĐIỀU HƯỚNG LỊCH SỬ KHÁM (VISIT TIMELINE) */}
      {visitTimeline && visitTimeline.length > 0 && (
        <section className="bg-white rounded-2xl border border-slate-200/90 p-4 shadow-xs print:hidden">
          <div className="flex items-center justify-between mb-2.5">
            <h3 className="text-xs font-extrabold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-[#127e7a]" />
              <span>Lịch sử các đợt khám ({visitTimeline.length})</span>
            </h3>
            <span className="text-[11px] text-slate-400">Chọn đợt khám để chuyển đổi nhanh trên trang</span>
          </div>

          <div className="flex items-center gap-2.5 overflow-x-auto pb-2 scrollbar-thin">
            {visitTimeline.map((item) => {
              const isCurrent = item.visitId === recordDetail.visitId;
              const dateStr = item.visitDate
                ? new Date(item.visitDate).toLocaleDateString('vi-VN')
                : '—';

              return (
                <button
                  key={item.visitId}
                  type="button"
                  onClick={() => handleSelectTimelineVisit(item.visitId)}
                  className={`flex flex-col text-left px-3.5 py-2.5 rounded-xl border transition-all shrink-0 cursor-pointer min-w-[170px] ${
                    isCurrent
                      ? 'bg-teal-50/90 border-[#127e7a] shadow-xs ring-1 ring-[#127e7a]/20'
                      : 'bg-white border-slate-200 hover:bg-slate-50 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between gap-1 w-full">
                    <span className={`text-xs font-bold ${isCurrent ? 'text-[#127e7a]' : 'text-slate-800'}`}>
                      {dateStr}
                    </span>
                    <span
                      className={`text-[9.5px] font-extrabold px-1.5 py-0.5 rounded ${
                        item.visitStatus === 'COMPLETED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : item.visitStatus === 'ABANDONED'
                          ? 'bg-rose-100 text-rose-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {item.visitStatus === 'COMPLETED' ? 'Hoàn thành' : item.visitStatus === 'ABANDONED' ? 'Bỏ khám' : 'Đang khám'}
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-500 font-medium truncate mt-0.5">
                    {item.visitCode} • {item.roomName || 'Phòng khám'}
                  </span>
                  {item.diagnosisName && (
                    <span className="text-[10px] text-slate-400 truncate mt-0.5 font-mono">
                      {item.diagnosisIcd ? `[${item.diagnosisIcd}] ` : ''}{item.diagnosisName}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </section>
      )}

      {/* 4. KHU VỰC C: CHI TIẾT LƯỢT KHÁM ĐƯỢC CHỌN (SELECTED VISIT DETAIL) */}
      <div className="space-y-6">
        {/* C.1 Thông tin hành chính lượt khám */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
            <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2">
              <Calendar className="w-4 h-4 text-[#127e7a]" />
              <span>Thông tin hành chính lượt khám</span>
            </h3>

            {/* Trạng thái & Nhãn Bỏ khám AF-02 / BR-22 */}
            <div className="flex items-center gap-2">
              {isAbandoned ? (
                <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-rose-50 text-rose-700 border border-rose-200">
                  Abandoned – no conclusion
                </span>
              ) : (
                <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  {visitStatus === 'COMPLETED' ? 'Hoàn thành' : 'Đang hoạt động'}
                </span>
              )}

              {isPriority && (
                <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200">
                  Ưu tiên {priorityReason ? `(${priorityReason})` : ''}
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
            <div>
              <span className="text-slate-400 block font-medium">Mã lượt khám:</span>
              <strong className="text-slate-800 text-sm font-mono">{visitCode}</strong>
            </div>
            <div>
              <span className="text-slate-400 block font-medium">Phòng khám tiếp nhận:</span>
              <strong className="text-slate-800">{roomName || '—'}</strong>
            </div>
            <div>
              <span className="text-slate-400 block font-medium">Bác sĩ phụ trách:</span>
              <strong className="text-slate-800">{doctorName || '—'}</strong>
            </div>
            <div>
              <span className="text-slate-400 block font-medium">Đối tượng khám:</span>
              <strong className="text-slate-800">
                {visitType || 'Dịch vụ'}
                {insuranceCardNumber ? ` (BHYT: ${insuranceCardNumber})` : ''}
              </strong>
            </div>
          </div>

          {/* Lịch sử chuyển phòng (Transfers) */}
          {transfers && transfers.length > 0 && (
            <div className="mt-3 pt-3 border-t border-slate-100">
              <span className="text-xs font-bold text-slate-700 block mb-2">Lịch sử chuyển phòng khám:</span>
              <div className="space-y-1.5">
                {transfers.map((tr) => (
                  <div key={tr.transferId} className="flex flex-wrap items-center gap-2 text-xs text-slate-600 bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-200/60">
                    <span className="font-semibold text-slate-800">{tr.fromRoomName} → {tr.toRoomName}</span>
                    <span className="text-slate-400">•</span>
                    <span>Lý do: {tr.reason || 'Không ghi nhận'}</span>
                    <span className="text-slate-400">•</span>
                    <span className="text-slate-400 font-mono text-[11px]">
                      {tr.transferredAt ? new Date(tr.transferredAt).toLocaleString('vi-VN') : ''}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        {/* C.2 Chỉ số sinh hiệu (Vital Signs - 8 chỉ số chuẩn) */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
          <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Heart className="w-4 h-4 text-rose-500" />
            <span>Chỉ số sinh hiệu (Vital Signs)</span>
            {vitalSigns?.thoidiem && (
              <span className="text-xs font-normal text-slate-400">
                • Đo lúc {new Date(vitalSigns.thoidiem).toLocaleTimeString('vi-VN')}
              </span>
            )}
          </h3>

          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Mạch</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.mach != null ? vitalSigns.mach : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">lần/phút</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Nhiệt độ</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.nhietdo != null ? vitalSigns.nhietdo : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">°C</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Huyết áp</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.huyetapTamthu != null && vitalSigns?.huyetapTamtruong != null
                  ? `${vitalSigns.huyetapTamthu}/${vitalSigns.huyetapTamtruong}`
                  : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">mmHg</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Nhịp thở</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.nhiptho != null ? vitalSigns.nhiptho : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">lần/phút</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Cân nặng</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.cannang != null ? vitalSigns.cannang : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">kg</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">Chiều cao</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.chieucao != null ? vitalSigns.chieucao : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">cm</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">BMI</span>
              <strong className="text-base font-extrabold text-[#127e7a] font-mono">
                {vitalSigns?.bmi != null ? vitalSigns.bmi : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">kg/m²</span>
            </div>

            <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-3 text-center">
              <span className="text-[11px] text-slate-500 block font-medium">SpO₂</span>
              <strong className="text-base font-extrabold text-slate-900 font-mono">
                {vitalSigns?.spo2 != null ? `${vitalSigns.spo2}%` : '—'}
              </strong>
              <span className="text-[10px] text-slate-400 block">Độ bão hòa</span>
            </div>
          </div>
        </section>

        {/* C.3 Khám lâm sàng */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
          <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Stethoscope className="w-4 h-4 text-[#127e7a]" />
            <span>Khám lâm sàng & Diễn biến bệnh</span>
          </h3>

          <div className="space-y-3 text-xs">
            <div>
              <span className="font-bold text-slate-700 block">Lý do vào viện / Triệu chứng ban đầu:</span>
              <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60 font-medium">
                {reasonForVisit || 'Không có mô tả'}
              </p>
            </div>

            {clinicalExam?.processOfIllness && (
              <div>
                <span className="font-bold text-slate-700 block">Quá trình bệnh lý:</span>
                <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  {clinicalExam.processOfIllness}
                </p>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <span className="font-bold text-slate-700 block">Khám toàn thân:</span>
                <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60 min-h-[50px]">
                  {clinicalExam?.generalExamination || 'Bình thường'}
                </p>
              </div>
              <div>
                <span className="font-bold text-slate-700 block">Khám bộ phận:</span>
                <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60 min-h-[50px]">
                  {clinicalExam?.partExamination || 'Bình thường'}
                </p>
              </div>
            </div>

            {clinicalExam?.initialDiagnosis && (
              <div>
                <span className="font-bold text-slate-700 block">Chẩn đoán sơ bộ:</span>
                <p className="text-slate-800 mt-1 bg-teal-50/50 p-2.5 rounded-xl border border-teal-100 font-medium">
                  {clinicalExam.initialDiagnosis}
                </p>
              </div>
            )}
          </div>
        </section>

        {/* C.4 Chỉ định & Kết quả Cận lâm sàng (Paraclinical Orders & Results) */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2">
              <Activity className="w-4 h-4 text-indigo-600" />
              <span>Chỉ định & Kết quả Cận lâm sàng</span>
            </h3>
            <span className="text-xs text-slate-400">
              {paraclinicalOrders.reduce((acc, o) => acc + (o.services?.length || 0), 0)} dịch vụ
            </span>
          </div>

          {paraclinicalOrders && paraclinicalOrders.length > 0 ? (
            <div className="space-y-4">
              {paraclinicalOrders.map((order) => (
                <div key={order.orderId} className="border border-slate-200/80 rounded-xl overflow-hidden">
                  <div className="bg-slate-50 px-4 py-2 border-b border-slate-200/80 flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-800 font-mono">Phiếu: {order.orderCode}</span>
                    <span className="text-slate-500">
                      {order.orderedAt ? new Date(order.orderedAt).toLocaleString('vi-VN') : ''}
                    </span>
                  </div>

                  <div className="divide-y divide-slate-100">
                    {order.services?.map((svc) => (
                      <div key={svc.orderDetailId} className="p-4 space-y-3 text-xs">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="font-extrabold text-slate-900 text-sm">{svc.serviceName}</span>
                            <span className="text-[11px] font-mono text-slate-500">({svc.orderCode})</span>
                            {svc.roomName && (
                              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-indigo-50 text-indigo-700">
                                {svc.roomName}
                              </span>
                            )}
                          </div>
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              svc.status === 'COMPLETED'
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-amber-50 text-amber-700 border border-amber-200'
                            }`}
                          >
                            {svc.status === 'COMPLETED' ? 'Đã có kết quả' : 'Đang xử lý'}
                          </span>
                        </div>

                        {/* Kết quả Xét nghiệm (Lab Results) */}
                        {svc.labResult && svc.labResult.items && svc.labResult.items.length > 0 && (
                          <div className="bg-slate-50/80 rounded-xl border border-slate-200/80 overflow-hidden">
                            <div className="px-3 py-1.5 bg-slate-100/80 font-bold text-slate-700 border-b border-slate-200/80">
                              Bảng chỉ số xét nghiệm:
                            </div>
                            <table className="w-full text-left border-collapse text-xs">
                              <thead>
                                <tr className="border-b border-slate-200/60 text-slate-500 text-[11px]">
                                  <th className="py-2 px-3">Tên chỉ số</th>
                                  <th className="py-2 px-3">Kết quả</th>
                                  <th className="py-2 px-3">Khoảng tham chiếu</th>
                                  <th className="py-2 px-3">Đơn vị</th>
                                  <th className="py-2 px-3">Đánh giá</th>
                                </tr>
                              </thead>
                              <tbody>
                                {svc.labResult.items.map((it, idx) => (
                                  <tr key={idx} className="border-b border-slate-100 hover:bg-slate-50">
                                    <td className="py-1.5 px-3 font-medium text-slate-800">{it.testIndexName}</td>
                                    <td className="py-1.5 px-3 font-mono font-bold text-slate-900">{it.testValue}</td>
                                    <td className="py-1.5 px-3 text-slate-500 font-mono">{it.normalRange || '—'}</td>
                                    <td className="py-1.5 px-3 text-slate-500">{it.unit || '—'}</td>
                                    <td className="py-1.5 px-3">
                                      {it.abnormalEvaluation === 'Bất thường' ? (
                                        <span className="font-bold text-rose-600">Bất thường</span>
                                      ) : (
                                        <span className="text-emerald-700">Bình thường</span>
                                      )}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}

                        {/* Kết quả Chẩn đoán hình ảnh (Imaging Results) */}
                        {svc.imagingResult && (
                          <div className="bg-slate-50/80 rounded-xl border border-slate-200/80 p-3 space-y-2">
                            {svc.imagingResult.description && (
                              <div>
                                <strong className="text-slate-700 block">Mô tả tổn thương/hình ảnh:</strong>
                                <p className="text-slate-800 mt-0.5">{svc.imagingResult.description}</p>
                              </div>
                            )}
                            {svc.imagingResult.conclusion && (
                              <div>
                                <strong className="text-slate-700 block">Kết luận CĐHA:</strong>
                                <p className="text-slate-900 font-bold mt-0.5">{svc.imagingResult.conclusion}</p>
                              </div>
                            )}
                            {svc.imagingResult.recommendation && (
                              <div>
                                <strong className="text-slate-700 block">Đề nghị:</strong>
                                <p className="text-slate-600 mt-0.5">{svc.imagingResult.recommendation}</p>
                              </div>
                            )}

                            {/* Danh sách tệp đính kèm kết quả (I.3.3 / BR-42) */}
                            {svc.imagingResult.images && svc.imagingResult.images.length > 0 && (
                              <div className="pt-2 border-t border-slate-200/60">
                                <span className="font-bold text-slate-700 block mb-1.5">Tệp hình ảnh kết quả:</span>
                                <div className="flex flex-wrap items-center gap-2">
                                  {svc.imagingResult.images.map((img) => (
                                    <button
                                      key={img.imageId}
                                      type="button"
                                      onClick={() => handleOpenResultImage(img)}
                                      className="inline-flex items-center gap-1.5 px-2.5 py-1.5 bg-white border border-slate-200 hover:border-[#127e7a] hover:text-[#127e7a] rounded-lg text-xs font-semibold text-slate-700 transition-colors cursor-pointer"
                                    >
                                      <ImageIcon className="w-3.5 h-3.5 text-indigo-500" />
                                      <span className="truncate max-w-[150px]">{img.fileName || 'Hình ảnh kết quả'}</span>
                                      <ExternalLink className="w-3 h-3 text-slate-400" />
                                    </button>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-xs text-slate-400 italic">Không có chỉ định cận lâm sàng trong đợt khám này.</p>
          )}
        </section>

        {/* C.5 Chẩn đoán & Kết luận điều trị */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
          <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
            <FileCheck className="w-4 h-4 text-emerald-600" />
            <span>Chẩn đoán & Kết luận điều trị</span>
          </h3>

          <div className="space-y-3 text-xs">
            <div>
              <span className="font-bold text-slate-700 block">Chẩn đoán cuối cùng:</span>
              <div className="mt-1 flex items-center gap-2">
                <span className="px-2 py-1 rounded bg-teal-50 text-[#127e7a] font-mono font-bold border border-teal-200 text-sm">
                  {conclusion?.icdCode || '—'}
                </span>
                <span className="text-slate-900 font-bold text-sm">
                  {conclusion?.icdName || conclusion?.diagnosis || 'Chưa kết luận'}
                </span>
              </div>
            </div>

            {conclusion?.treatmentPlan && (
              <div>
                <span className="font-bold text-slate-700 block">Hướng điều trị:</span>
                <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  {conclusion.treatmentPlan}
                </p>
              </div>
            )}

            {conclusion?.doctorAdvice && (
              <div>
                <span className="font-bold text-slate-700 block">Lời dặn của bác sĩ:</span>
                <p className="text-slate-800 mt-1 bg-amber-50/60 p-2.5 rounded-xl border border-amber-200/80">
                  {conclusion.doctorAdvice}
                </p>
              </div>
            )}
          </div>
        </section>

        {/* C.6 Đơn thuốc & Hẹn tái khám */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Đơn thuốc (2 cột) */}
          <section className="lg:col-span-2 bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2">
                <Pill className="w-4 h-4 text-teal-600" />
                <span>Đơn thuốc (Prescription)</span>
              </h3>
              {prescription?.prescriptionCode && (
                <span className="text-xs font-mono font-bold text-slate-700 bg-slate-100 px-2 py-0.5 rounded">
                  Mã: {prescription.prescriptionCode}
                </span>
              )}
            </div>

            {prescription && prescription.items && prescription.items.length > 0 ? (
              <div className="space-y-3">
                <div className="border border-slate-200 rounded-xl overflow-hidden">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-slate-500 text-[11px]">
                        <th className="py-2 px-3">STT</th>
                        <th className="py-2 px-3">Tên thuốc / Hoạt chất</th>
                        <th className="py-2 px-3">Số lượng</th>
                        <th className="py-2 px-3">Cách dùng & Liều lượng</th>
                      </tr>
                    </thead>
                    <tbody>
                      {prescription.items.map((med, idx) => (
                        <tr key={idx} className="border-b border-slate-100 hover:bg-slate-50">
                          <td className="py-2 px-3 font-mono text-slate-400">{idx + 1}</td>
                          <td className="py-2 px-3">
                            <strong className="text-slate-900 block">{med.medicineName}</strong>
                            <span className="text-[11px] text-slate-500 font-mono">
                              {med.activeIngredient} {med.strength}
                            </span>
                          </td>
                          <td className="py-2 px-3 font-mono font-bold text-slate-800">
                            {med.quantity} {med.unit}
                          </td>
                          <td className="py-2 px-3 text-slate-600">
                            <span>{med.usageInstructions || 'Uống theo chỉ định'}</span>
                            {med.durationDays > 0 && (
                              <span className="block text-[11px] text-slate-400 mt-0.5 font-mono">
                                Ngày {med.timesPerDay} lần • Dùng trong {med.durationDays} ngày
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {prescription.advice && (
                  <p className="text-xs text-slate-600 italic">
                    <strong>Lời dặn đơn thuốc:</strong> {prescription.advice}
                  </p>
                )}
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic">Không có đơn thuốc trong đợt khám này.</p>
            )}
          </section>

          {/* Lịch hẹn tái khám (1 cột) */}
          <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-4">
            <h3 className="text-sm font-extrabold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
              <Calendar className="w-4 h-4 text-blue-600" />
              <span>Hẹn tái khám</span>
            </h3>

            {followUpAppointment?.followUpDate ? (
              <div className="space-y-3 text-xs">
                <div className="bg-blue-50/70 border border-blue-200 rounded-xl p-3 text-center">
                  <span className="text-slate-500 block text-[11px] font-medium">Ngày hẹn khám lại</span>
                  <strong className="text-lg font-black text-blue-900 font-mono mt-0.5 block">
                    {new Date(followUpAppointment.followUpDate).toLocaleDateString('vi-VN')}
                  </strong>
                  {followUpAppointment.appointmentNumber && (
                    <span className="text-[10px] text-blue-700 font-mono mt-1 block">
                      Số phiếu: {followUpAppointment.appointmentNumber}
                    </span>
                  )}
                </div>

                {followUpAppointment.doctorAdvice && (
                  <div>
                    <span className="font-bold text-slate-700 block">Lời dặn tái khám:</span>
                    <p className="text-slate-800 mt-1 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                      {followUpAppointment.doctorAdvice}
                    </p>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic">Không có lịch hẹn tái khám.</p>
            )}
          </section>
        </div>
      </div>

      {/* 5. MODAL XEM ẢNH KẾT QUẢ CLS PHÓNG TO */}
      {previewImage && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 animate-in fade-in duration-150">
          <div className="relative max-w-3xl w-full bg-white rounded-2xl overflow-hidden shadow-2xl space-y-3 p-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
                <ImageIcon className="w-4 h-4 text-indigo-600" />
                <span>{previewImage.fileName || 'Hình ảnh kết quả cận lâm sàng'}</span>
              </div>
              <button
                type="button"
                onClick={() => setPreviewImage(null)}
                className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-700 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex items-center justify-center bg-slate-900/5 rounded-xl overflow-hidden max-h-[70vh]">
              <img
                src={previewImage.imageUrl}
                alt={previewImage.fileName || 'Kết quả cận lâm sàng'}
                className="object-contain max-h-[65vh] w-auto rounded-lg"
              />
            </div>

            {previewImage.caption && (
              <p className="text-xs text-slate-600 text-center italic">{previewImage.caption}</p>
            )}
          </div>
        </div>
      )}

      {/* 6. MODAL XUẤT HỒ SƠ BỆNH ÁN (UC-53) */}
      {exportModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 animate-in fade-in duration-150">
          <div className="w-full max-w-md bg-white rounded-2xl shadow-xl overflow-hidden p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <Download className="w-5 h-5 text-[#127e7a]" />
                <h3 className="text-base font-bold text-slate-900">Xuất Hồ sơ Bệnh án</h3>
              </div>
              <button
                type="button"
                onClick={() => setExportModalOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-slate-600">
              Vui lòng chọn phạm vi dữ liệu xuất bệnh án theo yêu cầu:
            </p>

            <div className="space-y-2.5">
              <label className="flex items-start gap-3 p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer transition-colors">
                <input
                  type="radio"
                  name="exportScope"
                  value="CURRENT_VISIT"
                  checked={exportScope === 'CURRENT_VISIT'}
                  onChange={(e) => setExportScope(e.target.value)}
                  className="mt-0.5 text-[#127e7a] focus:ring-[#127e7a]"
                />
                <div>
                  <strong className="text-xs text-slate-900 block">Chỉ đợt khám hiện tại</strong>
                  <span className="text-[11px] text-slate-500">
                    Xuất thông tin đợt khám ngày {visitDate ? new Date(visitDate).toLocaleDateString('vi-VN') : ''} ({visitCode})
                  </span>
                </div>
              </label>

              <label className="flex items-start gap-3 p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer transition-colors">
                <input
                  type="radio"
                  name="exportScope"
                  value="ALL_VISITS"
                  checked={exportScope === 'ALL_VISITS'}
                  onChange={(e) => setExportScope(e.target.value)}
                  className="mt-0.5 text-[#127e7a] focus:ring-[#127e7a]"
                />
                <div>
                  <strong className="text-xs text-slate-900 block">Toàn bộ lịch sử các đợt khám</strong>
                  <span className="text-[11px] text-slate-500">
                    Xuất toàn bộ {visitTimeline.length} đợt khám trong lịch sử của bệnh nhân {patientName}
                  </span>
                </div>
              </label>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setExportModalOpen(false)}
                disabled={isExporting}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleExportPdf}
                disabled={isExporting}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all cursor-pointer shadow-sm disabled:opacity-50"
              >
                {isExporting ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    <span>Đang xuất...</span>
                  </>
                ) : (
                  <>
                    <Download className="w-3.5 h-3.5" />
                    <span>Xác nhận xuất bệnh án</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default MedicalRecordDetailPage;
