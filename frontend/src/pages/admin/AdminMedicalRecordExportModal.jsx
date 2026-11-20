// modal chon json hoac xml
import { useState } from 'react';
import { Download, FileCode2, FileText, RefreshCw, ShieldCheck, CheckCircle2, X } from 'lucide-react';
import { toast } from 'sonner';
import { medicalRecordApi } from '../../apis/medicalRecordApi';

const RECIPIENT_REQUIRED_PURPOSES = new Set(['CUNG_CAP_NGUOI_BENH', 'CO_QUAN_CO_THAM_QUYEN']);

/**
 * Modal dành riêng cho Quản trị viên / Cán bộ lưu trữ EMR (UC-80 / BR-43).
 * Kết xuất dữ liệu điện tử (XML / JSON) theo Công văn 365/TTYQG-GPQLCL và Thông tư 13/2025/TT-BYT.
 * Bao gồm lựa chọn mục đích theo Điều 69 Luật KBCB, cơ quan tiếp nhận và đối soát mã băm SHA-256.
 */
export function AdminMedicalRecordExportModal({
  isOpen,
  onClose,
  recordId,
  recordNumber,
  visitCode,
}) {
  const [format, setFormat] = useState('XML'); // 'XML' | 'JSON'
  const [purpose, setPurpose] = useState('LIEN_THONG');
  const [recipient, setRecipient] = useState('');
  const [recipientError, setRecipientError] = useState('');
  const [isExporting, setIsExporting] = useState(false);
  const [exportSuccessInfo, setExportSuccessInfo] = useState(null);

  const recipientRequired = RECIPIENT_REQUIRED_PURPOSES.has(purpose);

  if (!isOpen) return null;

  /**
   * Tạo đường dẫn nhị phân tạm thời (Blob URL) và giả lập thao tác click vào thẻ <a>
   * để trình duyệt tải tệp (XML hoặc JSON) về máy tính của người dùng.
   * Tự động thu hồi Blob URL sau 1 giây nhằm giải phóng bộ nhớ RAM (tránh memory leak).
   * 
   * @param {Blob} blob - Dữ liệu nhị phân của tệp XML/JSON nhận từ API
   * @param {string} fileName - Tên tệp lưu về máy (ví dụ: HoSoBenhAn_BA001_DK001.xml)
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
          // bỏ qua nếu đã revoke
        }
      }, 1000);
    }
  };

  /**
   * Xử lý luồng kết xuất và tải dữ liệu bệnh án điện tử (XML / JSON):
   * 1. Kiểm tra tính hợp lệ của trường Người/Cơ quan tiếp nhận (bắt buộc đối với
   *    mục đích CUNG_CAP_NGUOI_BENH và CO_QUAN_CO_THAM_QUYEN theo Điều 69 Luật KBCB).
   * 2. Gọi API kết xuất `medicalRecordApi.exportMedicalRecordData` với định dạng và mục đích đã chọn.
   * 3. Nhận phản hồi nhị phân và kích hoạt tải file về máy tính.
   * 4. Lưu thông tin thành công kèm mã băm SHA-256 nhận từ header backend để phục vụ đối soát toàn vẹn dữ liệu.
   * 5. Xử lý và hiển thị thông báo lỗi nếu hồ sơ chưa đóng (EXPORT_RECORD_NOT_CLOSED) hoặc dữ liệu không hợp lệ.
   */
  const handleExportData = async () => {
    if (!recordId) return;

    const trimmedRecipient = recipient.trim();
    if (recipientRequired && !trimmedRecipient) {
      setRecipientError('Đơn vị / người tiếp nhận là bắt buộc đối với mục đích này.');
      return;
    }
    if (trimmedRecipient.length > 255) {
      setRecipientError('Đơn vị / người tiếp nhận không được vượt quá 255 ký tự.');
      return;
    }
    setRecipientError('');

    setIsExporting(true);
    setExportSuccessInfo(null);
    try {
      const result = await medicalRecordApi.exportMedicalRecordData(recordId, {
        format,
        purpose,
        recipient: trimmedRecipient || null,
      });

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
      if (err?.errorResponse?.code === 'EXPORT_RECORD_NOT_CLOSED') {
        toast.error('Chỉ được kết xuất dữ liệu đối với hồ sơ bệnh án đã đóng.');
      } else if (err?.errorResponse?.code === 'EXPORT_INVALID') {
        toast.error('Thông tin kết xuất không hợp lệ (người nhận là bắt buộc).');
      } else {
        toast.error('Không thể xuất dữ liệu hồ sơ bệnh án. Vui lòng thử lại sau.');
      }
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-lg bg-white rounded-2xl shadow-xl overflow-hidden p-6 space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2">
            <FileCode2 className="w-5 h-5 text-[#127e7a]" />
            <div>
              <h3 className="text-base font-bold text-slate-900">Kết xuất Dữ liệu Bệnh án EMR (Admin)</h3>
              <p className="text-[11px] text-slate-500">Chuẩn trao đổi dữ liệu Công văn 365/TTYQG & TT 13/2025/TT-BYT</p>
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
        <div className="space-y-1.5">
          <label className="text-xs font-bold text-slate-700">Định dạng trao đổi dữ liệu:</label>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => {
                setFormat('XML');
                setExportSuccessInfo(null);
              }}
              className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition-all cursor-pointer ${
                format === 'XML'
                  ? 'border-[#127e7a] bg-teal-50/60 text-[#127e7a] font-bold ring-1 ring-[#127e7a]'
                  : 'border-slate-200 hover:bg-slate-50 text-slate-700'
              }`}
            >
              <FileCode2 className="w-5 h-5 mb-1 text-[#127e7a]" />
              <span className="text-xs font-bold">Dữ liệu XML</span>
              <span className="text-[10px] text-slate-500">Chuẩn CV 365/BYT</span>
            </button>

            <button
              type="button"
              onClick={() => {
                setFormat('JSON');
                setExportSuccessInfo(null);
              }}
              className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition-all cursor-pointer ${
                format === 'JSON'
                  ? 'border-[#127e7a] bg-teal-50/60 text-[#127e7a] font-bold ring-1 ring-[#127e7a]'
                  : 'border-slate-200 hover:bg-slate-50 text-slate-700'
              }`}
            >
              <FileText className="w-5 h-5 mb-1 text-sky-600" />
              <span className="text-xs font-bold">Dữ liệu JSON</span>
              <span className="text-[10px] text-slate-500">Liên thông EMR</span>
            </button>
          </div>
        </div>

        {/* Cấu hình mục đích pháp lý */}
        <div className="space-y-3 bg-slate-50/70 p-3.5 rounded-xl border border-slate-100">
          <div className="space-y-1">
            <label htmlFor="adminExportPurpose" className="text-xs font-semibold text-slate-700">
              Mục đích xuất dữ liệu (Điều 69 Luật Khám bệnh, chữa bệnh):
            </label>
            <select
              id="adminExportPurpose"
              value={purpose}
              onChange={(e) => {
                const next = e.target.value;
                setPurpose(next);
                if (!RECIPIENT_REQUIRED_PURPOSES.has(next)) {
                  setRecipientError('');
                }
              }}
              className="w-full text-xs bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
            >
              <option value="LIEN_THONG">Liên thông y tế / Chuyển tuyến điều trị</option>
              <option value="LUU_TRU">Lưu trữ hồ sơ điện tử cơ sở</option>
              <option value="CUNG_CAP_NGUOI_BENH">Cung cấp thông tin cho người bệnh</option>
              <option value="CO_QUAN_CO_THAM_QUYEN">Cung cấp cơ quan có thẩm quyền</option>
            </select>
          </div>

          <div className="space-y-1">
            <label htmlFor="adminExportRecipient" className="text-xs font-semibold text-slate-700">
              Cơ quan / Đơn vị tiếp nhận {recipientRequired ? <span className="text-rose-500 font-bold">*</span> : <span className="text-slate-400 font-normal">(Tùy chọn)</span>}:
            </label>
            <input
              id="adminExportRecipient"
              type="text"
              maxLength={255}
              value={recipient}
              onChange={(e) => {
                setRecipient(e.target.value);
                if (recipientError) setRecipientError('');
              }}
              placeholder={recipientRequired ? "Nhập tên người bệnh hoặc cơ quan tiếp nhận..." : "VD: Cổng Giám định BHYT, Sở Y tế, Tòa án..."}
              className={`w-full text-xs bg-white border rounded-lg px-2.5 py-1.5 text-slate-800 focus:outline-none focus:ring-1 ${
                recipientError
                  ? 'border-rose-400 focus:ring-rose-500'
                  : 'border-slate-200 focus:ring-[#127e7a]'
              }`}
            />
            {recipientError && (
              <p className="text-[11px] text-rose-500 font-medium">{recipientError}</p>
            )}
          </div>

          <div className="flex items-start gap-2 text-[11px] text-slate-500 pt-0.5">
            <ShieldCheck className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
            <span>
              Tệp được tự động tính mã băm <strong>SHA-256</strong> để bảo đảm tính toàn vẹn và ghi vết kiểm toán theo quy định của Bộ Y tế.
            </span>
          </div>
        </div>

        {/* Thông báo kết quả tải file thành công kèm mã SHA-256 */}
        {exportSuccessInfo && (
          <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl space-y-1 text-emerald-900 animate-in fade-in">
            <div className="flex items-center gap-1.5 font-bold text-xs text-emerald-800">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Đã tải tệp {exportSuccessInfo.format} thành công!</span>
            </div>
            <p className="text-[11px] text-emerald-700 truncate">
              Tên tệp: <strong>{exportSuccessInfo.fileName}</strong>
            </p>
            {exportSuccessInfo.sha256 && (
              <div className="space-y-0.5 pt-1 border-t border-emerald-200/60">
                <span className="text-[10px] font-semibold text-emerald-700 uppercase tracking-wider">
                  Mã băm toàn vẹn (SHA-256):
                </span>
                <p className="font-mono text-[10px] bg-white/80 p-1.5 rounded border border-emerald-200 text-slate-800 break-all select-all">
                  {exportSuccessInfo.sha256}
                </p>
              </div>
            )}
          </div>
        )}

        {/* Hành động */}
        <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            type="button"
            onClick={() => {
              onClose();
              setExportSuccessInfo(null);
            }}
            disabled={isExporting}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
          >
            Đóng
          </button>
          <button
            type="button"
            onClick={handleExportData}
            disabled={isExporting}
            className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
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

