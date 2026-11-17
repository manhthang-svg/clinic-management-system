import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { X, Calendar, User, Stethoscope, FileText, Activity, AlertCircle, Clock, Pill, Printer, ExternalLink } from 'lucide-react';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { toast } from 'sonner';

function formatDate(val) {
  if (!val) return '—';
  try {
    const d = new Date(val);
    if (isNaN(d.getTime())) return val;
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
  } catch {
    return val;
  }
}

function formatDateTime(val) {
  if (!val) return '—';
  try {
    const d = new Date(val);
    if (isNaN(d.getTime())) return val;
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    const hour = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year} ${hour}:${min}`;
  } catch {
    return val;
  }
}

/**
 * Modal hiển thị chi tiết hồ sơ bệnh án (UC-52)
 */
export function MedicalRecordDetailModal({ visitId, recordId, onClose }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    const controller = new AbortController();

    async function fetchDetail() {
      setLoading(true);
      setError(null);
      try {
        let res;
        if (visitId) {
          res = await medicalRecordApi.getMedicalRecordByVisitId(visitId, controller.signal);
        } else if (recordId) {
          res = await medicalRecordApi.getMedicalRecordDetail(recordId, controller.signal);
        }
        if (!ignore && res) {
          setDetail(res);
        }
      } catch (err) {
        if (!ignore && err?.name !== 'AbortError') {
          console.error('Lỗi tải chi tiết bệnh án:', err);
          setError(err?.message || 'Không thể tải chi tiết hồ sơ bệnh án.');
          toast.error('Lỗi tải chi tiết bệnh án: ' + (err?.message || 'Vui lòng thử lại.'));
        }
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    if (visitId || recordId) {
      fetchDetail();
    }

    return () => {
      ignore = true;
      controller.abort();
    };
  }, [visitId, recordId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs">
      <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 bg-slate-50/80">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a]">
              <FileText className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Chi tiết Hồ sơ Bệnh án</h2>
              <p className="text-xs text-slate-500">
                {detail?.recordNumber ? `Mã BA: ${detail.recordNumber}` : 'Đang tải thông tin...'}
                {detail?.visitCode ? ` · Mã lượt khám: ${detail.visitCode}` : ''}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl p-2 text-slate-400 hover:bg-slate-200/60 hover:text-slate-600 cursor-pointer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading && (
            <div className="flex flex-col items-center justify-center py-16 space-y-3">
              <div className="h-8 w-8 animate-spin rounded-full border-3 border-[#127e7a] border-t-transparent" />
              <p className="text-sm font-medium text-slate-500">Đang tải hồ sơ bệnh án...</p>
            </div>
          )}

          {error && !loading && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-center">
              <AlertCircle className="mx-auto h-8 w-8 text-rose-500 mb-2" />
              <p className="text-sm font-medium text-rose-700">{error}</p>
              <button
                type="button"
                onClick={onClose}
                className="mt-3 inline-flex px-4 py-1.5 text-xs font-semibold text-rose-700 bg-rose-100 hover:bg-rose-200 rounded-lg cursor-pointer"
              >
                Đóng
              </button>
            </div>
          )}

          {detail && !loading && (
            <>
              {/* Thông tin hành chính & Bệnh nhân */}
              <div className="rounded-xl border border-slate-200 p-4 bg-slate-50/50 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-[#127e7a]">
                  <User className="h-4 w-4" />
                  <span>I. Thông tin Bệnh nhân</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                  <div>
                    <span className="text-xs text-slate-500 block">Họ và tên</span>
                    <span className="font-semibold text-slate-900">{detail.patientName || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Mã bệnh nhân</span>
                    <span className="font-mono font-semibold text-slate-800">{detail.patientCode || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Ngày sinh / Tuổi</span>
                    <span className="text-slate-800">
                      {formatDate(detail.dateOfBirth)} {detail.age !== null ? `(${detail.age} tuổi)` : ''}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Giới tính</span>
                    <span className="text-slate-800">{detail.gender || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Số CCCD/Định danh</span>
                    <span className="font-mono text-slate-800">{detail.citizenId || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Số điện thoại</span>
                    <span className="font-mono text-slate-800">{detail.phoneNumber || '—'}</span>
                  </div>
                  <div className="sm:col-span-2">
                    <span className="text-xs text-slate-500 block">Địa chỉ</span>
                    <span className="text-slate-800">{detail.address || '—'}</span>
                  </div>
                </div>

                {/* Dị ứng & Tiền sử */}
                <div className="border-t border-slate-200/70 pt-3 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                  <div>
                    <span className="text-xs font-semibold text-rose-600 block">Dị ứng thuốc:</span>
                    <span className="text-slate-800 text-xs">
                      {detail.drugAllergies && detail.drugAllergies.length > 0
                        ? detail.drugAllergies.join(', ')
                        : 'Không ghi nhận dị ứng'}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs font-semibold text-slate-600 block">Tiền sử bệnh:</span>
                    <span className="text-slate-800 text-xs">
                      {detail.medicalHistorySummary || 'Không ghi nhận'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Thông tin Lượt khám & Khám lâm sàng */}
              <div className="rounded-xl border border-slate-200 p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-[#127e7a]">
                  <Stethoscope className="h-4 w-4" />
                  <span>II. Lượt khám & Khám lâm sàng</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
                  <div>
                    <span className="text-xs text-slate-500 block">Ngày khám</span>
                    <span className="font-semibold text-slate-800">{formatDate(detail.visitDate)}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Phòng khám</span>
                    <span className="text-slate-800">{detail.roomName || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Bác sĩ khám</span>
                    <span className="font-semibold text-slate-800">{detail.doctorName || '—'}</span>
                  </div>
                </div>

                {/* Khám chi tiết */}
                <div className="border-t border-slate-100 pt-3 space-y-3 text-sm">
                  <div>
                    <span className="text-xs font-semibold text-slate-600 block">Lý do đến khám:</span>
                    <p className="text-slate-800 text-xs mt-0.5">{detail.reasonForVisit || '—'}</p>
                  </div>
                  {detail.clinicalExam && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div>
                        <span className="text-xs font-semibold text-slate-600 block">Quá trình bệnh lý:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.clinicalExam.processOfIllness || '—'}</p>
                      </div>
                      <div>
                        <span className="text-xs font-semibold text-slate-600 block">Khám toàn thân:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.clinicalExam.generalExamination || '—'}</p>
                      </div>
                      <div>
                        <span className="text-xs font-semibold text-slate-600 block">Khám bộ phận:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.clinicalExam.partExamination || '—'}</p>
                      </div>
                      <div>
                        <span className="text-xs font-semibold text-slate-600 block">Chẩn đoán sơ bộ:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.clinicalExam.initialDiagnosis || '—'}</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Kết luận & Chẩn đoán */}
              <div className="rounded-xl border border-slate-200 p-4 bg-teal-50/20 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-[#127e7a]">
                  <Activity className="h-4 w-4" />
                  <span>III. Kết luận điều trị</span>
                </div>
                {detail.conclusion ? (
                  <div className="space-y-3 text-sm">
                    <div className="flex flex-wrap items-baseline gap-2">
                      <span className="text-xs font-semibold text-slate-600">Chẩn đoán chính:</span>
                      <span className="font-mono font-bold text-[#127e7a] text-sm">
                        {detail.conclusion.icdCode}
                      </span>
                      <span className="font-semibold text-slate-900">
                        {detail.conclusion.icdName}
                      </span>
                    </div>

                    {detail.conclusion.diagnosis && (
                      <div>
                        <span className="text-xs text-slate-500 block">Diễn giải chẩn đoán:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.conclusion.diagnosis}</p>
                      </div>
                    )}

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div>
                        <span className="text-xs text-slate-500 block">Hướng điều trị:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.conclusion.treatmentPlan || '—'}</p>
                      </div>
                      <div>
                        <span className="text-xs text-slate-500 block">Lời dặn bác sĩ:</span>
                        <p className="text-slate-800 text-xs mt-0.5">{detail.conclusion.doctorAdvice || '—'}</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">Chưa có kết luận chính thức cho lượt khám này.</p>
                )}
              </div>

              {/* Hẹn tái khám */}
              {detail.followUpDate && (
                <div className="rounded-xl border border-amber-200 bg-amber-50/40 p-4 flex items-start gap-3">
                  <Calendar className="h-5 w-5 text-amber-600 mt-0.5 shrink-0" />
                  <div className="text-sm">
                    <span className="font-semibold text-amber-900">
                      Hẹn tái khám ngày: {formatDate(detail.followUpDate)}
                    </span>
                    {detail.followUpNote && (
                      <p className="text-xs text-amber-800 mt-0.5">Ghi chú: {detail.followUpNote}</p>
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t border-slate-200 px-6 py-3 bg-slate-50">
          <button
            type="button"
            onClick={() => {
              onClose();
              if (recordId) {
                navigate(`/doctor/medical-records/${encodeURIComponent(recordId)}`);
              } else if (visitId) {
                navigate(`/doctor/medical-records/visits/${encodeURIComponent(visitId)}`);
              }
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all cursor-pointer shadow-2xs"
          >
            <ExternalLink className="w-3.5 h-3.5" />
            <span>Mở trang chi tiết đầy đủ</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-semibold text-slate-700 bg-white border border-slate-300 hover:bg-slate-100 rounded-xl cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

export default MedicalRecordDetailModal;

