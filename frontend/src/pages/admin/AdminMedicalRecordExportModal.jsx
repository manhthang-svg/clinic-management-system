import { useState, useMemo } from 'react';
import { Download, FileCode2, FileText, RefreshCw, ShieldCheck, CheckCircle2, X, Building2, User, UserCheck } from 'lucide-react';
import { toast } from 'sonner';
import { medicalRecordApi } from '../../apis/medicalRecordApi';

/**
 * Modal kết xuất dữ liệu điện tử (XML / JSON) EMR dành cho Quản trị viên (UC-80 / BR-43).
 * Tuân thủ Công văn 365/TTYQG-GPQLCL, Thông tư 13/2025/TT-BYT và Điều 69 Luật KBCB 2023.
 */
export function AdminMedicalRecordExportModal({
  isOpen,
  onClose,
  recordId,
  recordNumber,
  visitCode,
  patientName = '',
  patientCccd = '',
}) {
  const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);

  const [format, setFormat] = useState('XML'); // 'XML' | 'JSON'
  const [purpose, setPurpose] = useState('LIEN_THONG'); // 'LIEN_THONG' | 'LUU_TRU' | 'CUNG_CAP_NGUOI_BENH'

  // Trường cho mục đích LIEN_THONG
  const [transferFacilityName, setTransferFacilityName] = useState('');
  const [transferFacilityCode, setTransferFacilityCode] = useState('');

  // Trường cho mục đích CUNG_CAP_NGUOI_BENH
  const [recipientType, setRecipientType] = useState('SELF'); // 'SELF' | 'REPRESENTATIVE'
  const [representativeName, setRepresentativeName] = useState('');
  const [representativeCccd, setRepresentativeCccd] = useState('');
  const [documentDate, setDocumentDate] = useState('');

  // Trường cho mục đích CO_QUAN_CO_THAM_QUYEN
  const [authorityType, setAuthorityType] = useState('');
  const [authorityTypeOther, setAuthorityTypeOther] = useState('');
  const [authorityName, setAuthorityName] = useState('');
  const [documentNumber, setDocumentNumber] = useState('');
  const [authorityDocDate, setAuthorityDocDate] = useState('');
  const [documentNotes, setDocumentNotes] = useState('');

  // Trạng thái touched và submitted để kiểm soát thời điểm hiện thông báo lỗi
  const [touched, setTouched] = useState({});
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [exportSuccessInfo, setExportSuccessInfo] = useState(null);

  const markTouched = (field) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  };

  /**
   * Đổi mục đích: Xóa sạch toàn bộ các trường và lỗi của mục đích cũ
   */
  const handlePurposeChange = (nextPurpose) => {
    setPurpose(nextPurpose);
    setTransferFacilityName('');
    setTransferFacilityCode('');
    setRecipientType('SELF');
    setRepresentativeName('');
    setRepresentativeCccd('');
    setDocumentDate('');
    setAuthorityType('');
    setAuthorityTypeOther('');
    setAuthorityName('');
    setDocumentNumber('');
    setAuthorityDocDate('');
    setDocumentNotes('');
    setTouched({});
    setHasSubmitted(false);
    setExportSuccessInfo(null);
  };

  /**
   * Tính toán lỗi của các trường dữ liệu
   */
  const errors = useMemo(() => {
    const errs = {};

    if (purpose === 'LIEN_THONG') {
      const trimmedFacility = transferFacilityName.trim();
      if (!trimmedFacility) {
        errs.transferFacilityName = 'Tên cơ sở khám bệnh, chữa bệnh nhận là bắt buộc.';
      } else if (trimmedFacility.length > 255) {
        errs.transferFacilityName = 'Tên cơ sở KCB không được vượt quá 255 ký tự.';
      }
      if (transferFacilityCode.trim().length > 50) {
        errs.transferFacilityCode = 'Mã cơ sở KCB không được vượt quá 50 ký tự.';
      }
    } else if (purpose === 'CUNG_CAP_NGUOI_BENH') {
      if (recipientType === 'SELF') {
        const cccd = patientCccd?.trim();
        if (cccd && !/^\d{12}$/.test(cccd)) {
          errs.selfCccd = 'Số CCCD trong hồ sơ bệnh nhân không hợp lệ (phải gồm đúng 12 chữ số).';
        }
      } else if (recipientType === 'REPRESENTATIVE') {
        const trimmedRepName = representativeName.trim();
        if (!trimmedRepName) {
          errs.representativeName = 'Họ và tên người đại diện là bắt buộc.';
        } else if (trimmedRepName.length > 255) {
          errs.representativeName = 'Họ tên không được vượt quá 255 ký tự.';
        }

        const trimmedRepCccd = representativeCccd.trim();
        if (!trimmedRepCccd) {
          errs.representativeCccd = 'Số CCCD của người đại diện là bắt buộc.';
        } else if (!/^\d{12}$/.test(trimmedRepCccd)) {
          errs.representativeCccd = 'Số CCCD phải gồm đúng 12 chữ số.';
        }

        if (documentDate && documentDate > todayStr) {
          errs.documentDate = 'Ngày văn bản không được sau ngày hiện tại.';
        }
      }
    } else if (purpose === 'CO_QUAN_CO_THAM_QUYEN') {
      if (!authorityType) {
        errs.authorityType = 'Vui lòng chọn loại cơ quan.';
      } else if (authorityType === 'KHAC') {
        const trimmedOther = authorityTypeOther.trim();
        if (!trimmedOther) {
          errs.authorityTypeOther = 'Vui lòng nhập loại cơ quan cụ thể.';
        } else if (trimmedOther.length > 100) {
          errs.authorityTypeOther = 'Loại cơ quan không được vượt quá 100 ký tự.';
        }
      }

      const trimmedAuthName = authorityName.trim();
      if (!trimmedAuthName) {
        errs.authorityName = 'Tên cơ quan / đơn vị là bắt buộc.';
      } else if (trimmedAuthName.length > 255) {
        errs.authorityName = 'Tên cơ quan không được vượt quá 255 ký tự.';
      }
      const trimmedDocNum = documentNumber.trim();
      if (!trimmedDocNum) {
        errs.documentNumber = 'Số văn bản yêu cầu là bắt buộc.';
      } else if (trimmedDocNum.length > 100) {
        errs.documentNumber = 'Số văn bản không được vượt quá 100 ký tự.';
      }
      if (!authorityDocDate) {
        errs.authorityDocDate = 'Ngày văn bản là bắt buộc.';
      } else if (authorityDocDate > todayStr) {
        errs.authorityDocDate = 'Ngày văn bản không được sau ngày hiện tại.';
      }
    }

    return errs;
  }, [
    purpose,
    transferFacilityName,
    transferFacilityCode,
    recipientType,
    patientCccd,
    representativeName,
    representativeCccd,
    documentDate,
    authorityType,
    authorityTypeOther,
    authorityName,
    documentNumber,
    authorityDocDate,
    todayStr,
  ]);

  // Form hợp lệ khi không có lỗi nào tồn tại
  const isFormValid = Object.keys(errors).length === 0;

  // Quyết định có hiển thị lỗi hay không: chỉ hiện khi đã blur rời ô hoặc đã bấm nút
  const getFieldError = (field) => {
    if (touched[field] || hasSubmitted) {
      return errors[field] || '';
    }
    return '';
  };

  /**
   * Tạo đường dẫn nhị phân tạm thời và kích hoạt tải file về máy
   */
  const handleDownloadFile = (blob, fileName) => {
    if (typeof window === 'undefined') return;
    const createUrl = window.URL?.createObjectURL || globalThis.URL?.createObjectURL;
    if (typeof createUrl !== 'function') return;
    const url = createUrl(blob);
    const link = document.createElement('a');
    link.style.display = 'none';
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    const revokeUrl = window.URL?.revokeObjectURL || globalThis.URL?.revokeObjectURL;
    if (typeof revokeUrl === 'function') {
      setTimeout(() => {
        try {
          revokeUrl(url);
        } catch {
          // bỏ qua
        }
      }, 1000);
    }
  };

  /**
   * Xử lý gửi yêu cầu xuất dữ liệu
   */
  const handleExportData = async () => {
    setHasSubmitted(true);
    if (!recordId || !isFormValid) return;

    setIsExporting(true);
    setExportSuccessInfo(null);

    // Xây dựng payload theo đúng mục đích
    const payload = {
      format,
      purpose,
      recipientType: purpose === 'CUNG_CAP_NGUOI_BENH' ? recipientType : null,
      recipientName:
        purpose === 'CUNG_CAP_NGUOI_BENH'
          ? recipientType === 'SELF'
            ? patientName
            : representativeName.trim()
          : null,
      recipientCccd:
        purpose === 'CUNG_CAP_NGUOI_BENH'
          ? recipientType === 'SELF'
            ? patientCccd || null
            : representativeCccd.trim()
          : null,
      documentDate:
        purpose === 'CUNG_CAP_NGUOI_BENH' && recipientType === 'REPRESENTATIVE' && documentDate
          ? documentDate
          : purpose === 'CO_QUAN_CO_THAM_QUYEN' && authorityDocDate
          ? authorityDocDate
          : null,
      transferFacilityName: purpose === 'LIEN_THONG' ? transferFacilityName.trim() : null,
      transferFacilityCode:
        purpose === 'LIEN_THONG' && transferFacilityCode.trim()
          ? transferFacilityCode.trim()
          : null,
      authorityType: purpose === 'CO_QUAN_CO_THAM_QUYEN' ? authorityType : null,
      authorityTypeOther:
        purpose === 'CO_QUAN_CO_THAM_QUYEN' && authorityType === 'KHAC'
          ? authorityTypeOther.trim()
          : null,
      authorityName: purpose === 'CO_QUAN_CO_THAM_QUYEN' ? authorityName.trim() : null,
      documentNumber: purpose === 'CO_QUAN_CO_THAM_QUYEN' ? documentNumber.trim() : null,
      documentNotes:
        purpose === 'CO_QUAN_CO_THAM_QUYEN' && documentNotes.trim()
          ? documentNotes.trim()
          : null,
      recipient:
        purpose === 'LIEN_THONG'
          ? transferFacilityName.trim()
          : purpose === 'LUU_TRU'
          ? 'Lưu trữ nội bộ'
          : purpose === 'CUNG_CAP_NGUOI_BENH'
          ? recipientType === 'SELF'
            ? patientName
            : representativeName.trim()
          : `[${
              authorityType === 'CONG_AN'
                ? 'Công an'
                : authorityType === 'TOA_AN'
                ? 'Tòa án'
                : authorityType === 'VIEN_KIEM_SAT'
                ? 'Viện kiểm sát'
                : authorityType === 'THANH_TRA'
                ? 'Thanh tra'
                : authorityTypeOther.trim() || 'Cơ quan khác'
            }] ${authorityName.trim()} - Số: ${documentNumber.trim()}`,
    };

    try {
      const result = await medicalRecordApi.exportMedicalRecordData(recordId, payload);

      const fallbackName = `HoSoBenhAn_${recordNumber || 'BA'}_${visitCode || 'DK'}.${format.toLowerCase()}`;
      const finalFileName = result?.fileName || fallbackName;

      if (result?.blob) {
        handleDownloadFile(result.blob, finalFileName);
      }

      setExportSuccessInfo({
        fileName: finalFileName,
        sha256: result?.sha256 || null,
        format,
      });

      toast.success(`Đã kết xuất dữ liệu ${format} thành công.`);
    } catch (err) {
      console.error('Lỗi xuất dữ liệu bệnh án:', err);
      const code = err?.errorResponse?.code;
      if (code === 'EXPORT_RECORD_NOT_CLOSED') {
        toast.error('Chỉ được kết xuất dữ liệu đối với hồ sơ bệnh án đã đóng.');
      } else if (code === 'EXPORT_INVALID') {
        const reason = err?.errorResponse?.details?.reason || '';
        if (reason === 'INVALID_CCCD') {
          toast.error('Số CCCD không hợp lệ (phải gồm đúng 12 chữ số).');
        } else if (reason === 'FUTURE_DATE') {
          toast.error('Ngày văn bản không được sau ngày hiện tại.');
        } else {
          toast.error('Thông tin người/cơ quan tiếp nhận không hợp lệ theo quy định.');
        }
      } else {
        toast.error('Không thể xuất dữ liệu hồ sơ bệnh án. Vui lòng thử lại sau.');
      }
    } finally {
      setIsExporting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-3 sm:p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-2xl bg-white rounded-2xl shadow-xl overflow-hidden p-4 sm:p-5 space-y-2.5 max-h-[94vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-2">
            <FileCode2 className="w-5 h-5 text-[#127e7a]" />
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Kết xuất Dữ liệu Bệnh án EMR (Admin)</h3>
              <p className="text-[10px] sm:text-[11px] text-slate-500">Chuẩn trao đổi dữ liệu Công văn 365/TTYQG & TT 13/2025/TT-BYT</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              onClose();
              setExportSuccessInfo(null);
            }}
            className="p-1 rounded-lg text-slate-400 hover:bg-slate-100 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Định dạng */}
        <div className="grid grid-cols-2 gap-3.5 w-full">
          <button
            type="button"
            onClick={() => {
              setFormat('XML');
              setExportSuccessInfo(null);
            }}
            className={`flex flex-col items-center justify-center py-2.5 sm:py-3 px-4 rounded-xl border text-center transition-all cursor-pointer ${
              format === 'XML'
                ? 'border-[#127e7a] bg-teal-50/70 text-[#127e7a] font-bold ring-1.5 ring-[#127e7a] shadow-xs'
                : 'border-slate-200 hover:bg-slate-50 text-slate-700'
            }`}
          >
            <div className="flex items-center gap-2 mb-0.5">
              <FileCode2 className="w-4.5 h-4.5 text-[#127e7a]" />
              <span className="text-sm font-bold">Dữ liệu XML</span>
            </div>
            <span className="text-[11px] text-slate-500 font-medium">Theo Công văn 365/BYT</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setFormat('JSON');
              setExportSuccessInfo(null);
            }}
            className={`flex flex-col items-center justify-center py-2.5 sm:py-3 px-4 rounded-xl border text-center transition-all cursor-pointer ${
              format === 'JSON'
                ? 'border-[#127e7a] bg-teal-50/70 text-[#127e7a] font-bold ring-1.5 ring-[#127e7a] shadow-xs'
                : 'border-slate-200 hover:bg-slate-50 text-slate-700'
            }`}
          >
            <div className="flex items-center gap-2 mb-0.5">
              <FileText className="w-4.5 h-4.5 text-sky-600" />
              <span className="text-sm font-bold">Dữ liệu JSON</span>
            </div>
            <span className="text-[11px] text-slate-500 font-medium">Chuẩn liên thông EMR</span>
          </button>
        </div>

        {/* Cấu hình mục đích pháp lý */}
        <div className="space-y-2.5 bg-slate-50/80 p-3 rounded-xl border border-slate-100">
          <div className="space-y-1">
            <label htmlFor="adminExportPurpose" className="text-xs font-semibold text-slate-700">
              Mục đích xuất dữ liệu (Điều 69 Luật Khám bệnh, chữa bệnh):
            </label>
            <select
              id="adminExportPurpose"
              value={purpose}
              onChange={(e) => handlePurposeChange(e.target.value)}
              className="w-full text-xs bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
            >
              <option value="LIEN_THONG">Liên thông y tế / Chuyển tuyến điều trị</option>
              <option value="LUU_TRU">Lưu trữ hồ sơ điện tử cơ sở</option>
              <option value="CUNG_CAP_NGUOI_BENH">Cung cấp cho người bệnh / Người đại diện</option>
              <option value="CO_QUAN_CO_THAM_QUYEN">Cung cấp cho cơ quan có thẩm quyền</option>
            </select>
          </div>

          {/* 1. MỤC ĐÍCH: LIÊN THÔNG Y TẾ / CHUYỂN TUYẾN */}
          {purpose === 'LIEN_THONG' && (
            <div className="pt-1 border-t border-slate-200/60 animate-in fade-in duration-150">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="sm:col-span-2 space-y-1">
                  <label htmlFor="transferFacilityName" className="text-xs font-semibold text-slate-700">
                    Tên cơ sở KCB nhận <span className="text-rose-500 font-bold">*</span>:
                  </label>
                  <input
                    id="transferFacilityName"
                    type="text"
                    maxLength={255}
                    value={transferFacilityName}
                    onChange={(e) => setTransferFacilityName(e.target.value)}
                    onBlur={() => markTouched('transferFacilityName')}
                    placeholder="VD: Bệnh viện Đa khoa Tỉnh, Bệnh viện Chợ Rẫy..."
                    className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                      getFieldError('transferFacilityName')
                        ? 'border-rose-400 focus:ring-rose-500'
                        : 'border-slate-200 focus:ring-[#127e7a]'
                    }`}
                  />
                  {getFieldError('transferFacilityName') && (
                    <p className="text-[11px] text-rose-500 font-medium">{getFieldError('transferFacilityName')}</p>
                  )}
                </div>

                <div className="space-y-1">
                  <label htmlFor="transferFacilityCode" className="text-xs font-semibold text-slate-700">
                    Mã cơ sở KCB nhận <span className="text-slate-400 font-normal">(Tùy chọn)</span>:
                  </label>
                  <input
                    id="transferFacilityCode"
                    type="text"
                    maxLength={50}
                    value={transferFacilityCode}
                    onChange={(e) => setTransferFacilityCode(e.target.value)}
                    onBlur={() => markTouched('transferFacilityCode')}
                    placeholder="VD: 79001, 01001..."
                    className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                      getFieldError('transferFacilityCode')
                        ? 'border-rose-400 focus:ring-rose-500'
                        : 'border-slate-200 focus:ring-[#127e7a]'
                    }`}
                  />
                  {getFieldError('transferFacilityCode') && (
                    <p className="text-[11px] text-rose-500 font-medium">{getFieldError('transferFacilityCode')}</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* 2. MỤC ĐÍCH: LƯU TRỮ HỒ SƠ ĐIỆN TỬ CƠ SỞ */}
          {purpose === 'LUU_TRU' && (
            <div className="space-y-2 pt-1 border-t border-slate-200/60 animate-in fade-in duration-150">
              <label className="text-xs font-semibold text-slate-700">Đơn vị tiếp nhận lưu trữ:</label>
              <div className="flex items-center gap-2 p-2.5 bg-slate-100 rounded-lg border border-slate-200 text-slate-600 text-xs">
                <Building2 className="w-4 h-4 text-slate-500 shrink-0" />
                <span className="font-semibold text-slate-700">Lưu trữ nội bộ</span>
                <span className="text-[11px] text-slate-400 ml-auto">(Tự động điền - Read only)</span>
              </div>
              <p className="text-[11px] text-slate-500">
                Gói dữ liệu được lưu trữ nội bộ vào kho EMR cơ sở tuân thủ Luật Lưu trữ 2024.
              </p>
            </div>
          )}

          {/* 3. MỤC ĐÍCH: CUNG CẤP CHO NGƯỜI BỆNH / NGƯỜI ĐẠI DIỆN */}
          {purpose === 'CUNG_CAP_NGUOI_BENH' && (
            <div className="space-y-3 pt-1 border-t border-slate-200/60 animate-in fade-in duration-150">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-700">Đối tượng tiếp nhận:</label>
                <div className="grid grid-cols-2 gap-2">
                  <label
                    className={`flex items-center gap-2 p-2 rounded-lg border text-xs cursor-pointer transition-all ${
                      recipientType === 'SELF'
                        ? 'border-[#127e7a] bg-teal-50/70 text-[#127e7a] font-bold'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="recipientTypeRadio"
                      value="SELF"
                      checked={recipientType === 'SELF'}
                      onChange={() => {
                        setRecipientType('SELF');
                        setTouched({});
                      }}
                      className="accent-[#127e7a]"
                    />
                    <User className="w-3.5 h-3.5" />
                    <span>Bản thân người bệnh</span>
                  </label>

                  <label
                    className={`flex items-center gap-2 p-2 rounded-lg border text-xs cursor-pointer transition-all ${
                      recipientType === 'REPRESENTATIVE'
                        ? 'border-[#127e7a] bg-teal-50/70 text-[#127e7a] font-bold'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="recipientTypeRadio"
                      value="REPRESENTATIVE"
                      checked={recipientType === 'REPRESENTATIVE'}
                      onChange={() => {
                        setRecipientType('REPRESENTATIVE');
                        setTouched({});
                      }}
                      className="accent-[#127e7a]"
                    />
                    <UserCheck className="w-3.5 h-3.5" />
                    <span>Người đại diện hợp pháp</span>
                  </label>
                </div>
              </div>

              {/* Nếu là Bản thân: Tự điền và khóa read-only */}
              {recipientType === 'SELF' && (
                <div className="space-y-2 p-2.5 bg-slate-100/80 rounded-lg border border-slate-200">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label className="text-[11px] font-semibold text-slate-600">Họ và tên người bệnh:</label>
                      <input
                        type="text"
                        readOnly
                        value={patientName || 'Chưa có thông tin'}
                        className="w-full text-xs bg-slate-200/70 border border-slate-300 rounded px-2.5 py-1.5 text-slate-700 font-semibold cursor-not-allowed select-none"
                      />
                    </div>

                    <div className="space-y-1">
                      <label className="text-[11px] font-semibold text-slate-600">Số CCCD (từ hồ sơ):</label>
                      <input
                        type="text"
                        readOnly
                        value={patientCccd || 'Chưa cập nhật CCCD'}
                        className="w-full text-xs font-mono bg-slate-200/70 border border-slate-300 rounded px-2.5 py-1.5 text-slate-700 font-semibold cursor-not-allowed select-none"
                      />
                      {getFieldError('selfCccd') && (
                        <p className="text-[11px] text-rose-500 font-medium">{getFieldError('selfCccd')}</p>
                      )}
                    </div>
                  </div>
                  <span className="block text-[10px] text-slate-500 italic">
                    * Thông tin được tự động trích xuất từ hồ sơ bệnh nhân và khóa không chỉnh sửa.
                  </span>
                </div>
              )}

              {/* Nếu là Người đại diện: Mở ô trống nhập tay */}
              {recipientType === 'REPRESENTATIVE' && (
                <div className="space-y-2.5 p-2.5 bg-white rounded-lg border border-slate-200">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label htmlFor="representativeName" className="text-xs font-semibold text-slate-700">
                        Họ và tên người đại diện <span className="text-rose-500 font-bold">*</span>:
                      </label>
                      <input
                        id="representativeName"
                        type="text"
                        maxLength={255}
                        value={representativeName}
                        onChange={(e) => setRepresentativeName(e.target.value)}
                        onBlur={() => markTouched('representativeName')}
                        placeholder="Nhập đầy đủ họ và tên người đại diện..."
                        className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                          getFieldError('representativeName')
                            ? 'border-rose-400 focus:ring-rose-500'
                            : 'border-slate-200 focus:ring-[#127e7a]'
                        }`}
                      />
                      {getFieldError('representativeName') && (
                        <p className="text-[11px] text-rose-500 font-medium">{getFieldError('representativeName')}</p>
                      )}
                    </div>

                    <div className="space-y-1">
                      <label htmlFor="representativeCccd" className="text-xs font-semibold text-slate-700">
                        Số CCCD người đại diện <span className="text-rose-500 font-bold">*</span> (12 số):
                      </label>
                      <input
                        id="representativeCccd"
                        type="text"
                        maxLength={12}
                        value={representativeCccd}
                        onChange={(e) => {
                          const val = e.target.value.replace(/\D/g, ''); // chỉ nhận số
                          setRepresentativeCccd(val);
                        }}
                        onBlur={() => markTouched('representativeCccd')}
                        placeholder="Nhập 12 chữ số CCCD..."
                        className={`w-full text-xs font-mono bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                          getFieldError('representativeCccd')
                            ? 'border-rose-400 focus:ring-rose-500'
                            : 'border-slate-200 focus:ring-[#127e7a]'
                        }`}
                      />
                      {getFieldError('representativeCccd') && (
                        <p className="text-[11px] text-rose-500 font-medium">{getFieldError('representativeCccd')}</p>
                      )}
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label htmlFor="documentDate" className="text-xs font-semibold text-slate-700">
                      Ngày văn bản ủy quyền <span className="text-slate-400 font-normal">(Không sau ngày hiện tại)</span>:
                    </label>
                    <input
                      id="documentDate"
                      type="date"
                      max={todayStr}
                      value={documentDate}
                      onChange={(e) => setDocumentDate(e.target.value)}
                      onBlur={() => markTouched('documentDate')}
                      className={`w-full sm:w-1/2 text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                        getFieldError('documentDate')
                          ? 'border-rose-400 focus:ring-rose-500'
                          : 'border-slate-200 focus:ring-[#127e7a]'
                      }`}
                    />
                    {getFieldError('documentDate') && (
                      <p className="text-[11px] text-rose-500 font-medium">{getFieldError('documentDate')}</p>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 4. MỤC ĐÍCH: CUNG CẤP CHO CƠ QUAN CÓ THẨM QUYỀN */}
          {purpose === 'CO_QUAN_CO_THAM_QUYEN' && (
            <div className="space-y-2 pt-1 border-t border-slate-200/60 animate-in fade-in duration-150">
              {authorityType === 'KHAC' ? (
                <>
                  {/* Hàng 1 khi chọn Khác: Loại cơ quan & Chi tiết loại cơ quan */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <div className="space-y-0.5">
                      <label htmlFor="authorityType" className="text-xs font-semibold text-slate-700">
                        Loại cơ quan <span className="text-rose-500 font-bold">*</span>:
                      </label>
                      <select
                        id="authorityType"
                        value={authorityType}
                        onChange={(e) => setAuthorityType(e.target.value)}
                        onBlur={() => markTouched('authorityType')}
                        className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                          getFieldError('authorityType')
                            ? 'border-rose-400 focus:ring-rose-500'
                            : 'border-slate-200 focus:ring-[#127e7a]'
                        }`}
                      >
                        <option value="">-- Chọn loại cơ quan --</option>
                        <option value="CONG_AN">Cơ quan Công an / CSĐT</option>
                        <option value="TOA_AN">Tòa án nhân dân</option>
                        <option value="VIEN_KIEM_SAT">Viện kiểm sát nhân dân</option>
                        <option value="THANH_TRA">Thanh tra y tế / Quản lý nhà nước</option>
                        <option value="KHAC">Khác (Tự nhập loại cơ quan)</option>
                      </select>
                      {getFieldError('authorityType') && (
                        <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityType')}</p>
                      )}
                    </div>

                    <div className="space-y-0.5 animate-in fade-in duration-150">
                      <label htmlFor="authorityTypeOther" className="text-xs font-semibold text-slate-700">
                        Chi tiết loại cơ quan <span className="text-rose-500 font-bold">*</span>:
                      </label>
                      <input
                        id="authorityTypeOther"
                        type="text"
                        maxLength={100}
                        value={authorityTypeOther}
                        onChange={(e) => setAuthorityTypeOther(e.target.value)}
                        onBlur={() => markTouched('authorityTypeOther')}
                        placeholder="VD: Cơ quan Thi hành án dân sự, Bảo hiểm Xã hội..."
                        className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                          getFieldError('authorityTypeOther')
                            ? 'border-rose-400 focus:ring-rose-500'
                            : 'border-slate-200 focus:ring-[#127e7a]'
                        }`}
                      />
                      {getFieldError('authorityTypeOther') && (
                        <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityTypeOther')}</p>
                      )}
                    </div>
                  </div>

                  {/* Hàng 2 khi chọn Khác: Tên cơ quan / đơn vị */}
                  <div className="space-y-0.5">
                    <label htmlFor="authorityName" className="text-xs font-semibold text-slate-700">
                      Tên cơ quan / đơn vị <span className="text-rose-500 font-bold">*</span>:
                    </label>
                    <input
                      id="authorityName"
                      type="text"
                      maxLength={255}
                      value={authorityName}
                      onChange={(e) => setAuthorityName(e.target.value)}
                      onBlur={() => markTouched('authorityName')}
                      placeholder="VD: Cục THADS TP. Hà Nội, BHXH TP. Hồ Chí Minh..."
                      className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                        getFieldError('authorityName')
                          ? 'border-rose-400 focus:ring-rose-500'
                          : 'border-slate-200 focus:ring-[#127e7a]'
                      }`}
                    />
                    {getFieldError('authorityName') && (
                      <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityName')}</p>
                    )}
                  </div>
                </>
              ) : (
                /* Hàng 1 khi chọn loại có sẵn: Loại cơ quan và Tên cơ quan chung 1 hàng 2 cột */
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  <div className="space-y-0.5">
                    <label htmlFor="authorityType" className="text-xs font-semibold text-slate-700">
                      Loại cơ quan <span className="text-rose-500 font-bold">*</span>:
                    </label>
                    <select
                      id="authorityType"
                      value={authorityType}
                      onChange={(e) => setAuthorityType(e.target.value)}
                      onBlur={() => markTouched('authorityType')}
                      className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                        getFieldError('authorityType')
                          ? 'border-rose-400 focus:ring-rose-500'
                          : 'border-slate-200 focus:ring-[#127e7a]'
                      }`}
                    >
                      <option value="">-- Chọn loại cơ quan --</option>
                      <option value="CONG_AN">Cơ quan Công an / CSĐT</option>
                      <option value="TOA_AN">Tòa án nhân dân</option>
                      <option value="VIEN_KIEM_SAT">Viện kiểm sát nhân dân</option>
                      <option value="THANH_TRA">Thanh tra y tế / Quản lý nhà nước</option>
                      <option value="KHAC">Khác (Tự nhập loại cơ quan)</option>
                    </select>
                    {getFieldError('authorityType') && (
                      <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityType')}</p>
                    )}
                  </div>

                  <div className="space-y-0.5">
                    <label htmlFor="authorityName" className="text-xs font-semibold text-slate-700">
                      Tên cơ quan / đơn vị <span className="text-rose-500 font-bold">*</span>:
                    </label>
                    <input
                      id="authorityName"
                      type="text"
                      maxLength={255}
                      value={authorityName}
                      onChange={(e) => setAuthorityName(e.target.value)}
                      onBlur={() => markTouched('authorityName')}
                      placeholder="VD: Cơ quan CSĐT Công an Quận Cầu Giấy"
                      className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                        getFieldError('authorityName')
                          ? 'border-rose-400 focus:ring-rose-500'
                          : 'border-slate-200 focus:ring-[#127e7a]'
                      }`}
                    />
                    {getFieldError('authorityName') && (
                      <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityName')}</p>
                    )}
                  </div>
                </div>
              )}

              {/* Hàng: Số văn bản & Ngày văn bản */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div className="space-y-0.5">
                  <label htmlFor="documentNumber" className="text-xs font-semibold text-slate-700">
                    Số văn bản yêu cầu <span className="text-rose-500 font-bold">*</span>:
                  </label>
                  <input
                    id="documentNumber"
                    type="text"
                    maxLength={100}
                    value={documentNumber}
                    onChange={(e) => setDocumentNumber(e.target.value)}
                    onBlur={() => markTouched('documentNumber')}
                    placeholder="VD: 123/CV-CSĐT"
                    className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                      getFieldError('documentNumber')
                        ? 'border-rose-400 focus:ring-rose-500'
                        : 'border-slate-200 focus:ring-[#127e7a]'
                    }`}
                  />
                  {getFieldError('documentNumber') && (
                    <p className="text-[11px] text-rose-500 font-medium">{getFieldError('documentNumber')}</p>
                  )}
                </div>

                <div className="space-y-0.5">
                  <label htmlFor="authorityDocDate" className="text-xs font-semibold text-slate-700">
                    Ngày văn bản <span className="text-rose-500 font-bold">*</span>:
                  </label>
                  <input
                    id="authorityDocDate"
                    type="date"
                    max={todayStr}
                    value={authorityDocDate}
                    onChange={(e) => setAuthorityDocDate(e.target.value)}
                    onBlur={() => markTouched('authorityDocDate')}
                    className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                      getFieldError('authorityDocDate')
                        ? 'border-rose-400 focus:ring-rose-500'
                        : 'border-slate-200 focus:ring-[#127e7a]'
                    }`}
                  />
                  {getFieldError('authorityDocDate') && (
                    <p className="text-[11px] text-rose-500 font-medium">{getFieldError('authorityDocDate')}</p>
                  )}
                </div>
              </div>

              {/* Hàng: Ghi chú 1 dòng */}
              <div className="space-y-0.5">
                <label htmlFor="documentNotes" className="text-xs font-semibold text-slate-700">
                  Ghi chú <span className="text-slate-400 font-normal">(Không bắt buộc)</span>:
                </label>
                <input
                  type="text"
                  id="documentNotes"
                  maxLength={255}
                  value={documentNotes}
                  onChange={(e) => setDocumentNotes(e.target.value)}
                  placeholder="Nội dung / lý do yêu cầu (VD: Phục vụ điều tra vụ án...)"
                  className="w-full text-xs bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
                />
              </div>
            </div>
          )}

          <div className="flex items-center gap-1.5 text-[10px] text-slate-500 pt-0.5">
            <ShieldCheck className="w-3.5 h-3.5 text-[#127e7a] shrink-0" />
            <span>
              Tệp được tự động tính mã băm <strong>SHA-256</strong> để bảo đảm tính toàn vẹn và ghi vết kiểm toán theo quy định Bộ Y tế.
            </span>
          </div>
        </div>

        {/* Thông báo kết quả tải file thành công kèm mã SHA-256 */}
        {exportSuccessInfo && (
          <div className="p-2.5 bg-emerald-50 border border-emerald-200 rounded-xl space-y-1 text-emerald-900 animate-in fade-in">
            <div className="flex items-center gap-1.5 font-bold text-xs text-emerald-800">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
              <span>Đã tải tệp {exportSuccessInfo.format} thành công!</span>
            </div>
            <p className="text-[10px] text-emerald-700 truncate">
              Tên tệp: <strong>{exportSuccessInfo.fileName}</strong>
            </p>
            {exportSuccessInfo.sha256 && (
              <div className="space-y-0.5 pt-0.5 border-t border-emerald-200/60">
                <span className="text-[9px] font-semibold text-emerald-700 uppercase tracking-wider">
                  Mã băm toàn vẹn (SHA-256):
                </span>
                <p className="font-mono text-[9px] bg-white/80 p-1 rounded border border-emerald-200 text-slate-800 break-all select-all">
                  {exportSuccessInfo.sha256}
                </p>
              </div>
            )}
          </div>
        )}

        {/* Hành động */}
        <div className="flex items-center justify-end gap-2 pt-1 border-t border-slate-100">
          <button
            type="button"
            onClick={() => {
              onClose();
              setExportSuccessInfo(null);
            }}
            disabled={isExporting}
            className="px-3.5 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
          >
            Đóng
          </button>
          <button
            type="button"
            onClick={handleExportData}
            disabled={!isFormValid || isExporting}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isExporting ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                <span>Đang xử lý kết xuất...</span>
              </>
            ) : (
              <>
                <Download className="w-3.5 h-3.5" />
                <span>Tải tệp {format}</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

export default AdminMedicalRecordExportModal;
