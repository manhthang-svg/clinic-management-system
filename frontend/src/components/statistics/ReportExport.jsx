import { useState } from 'react';
import { FileSpreadsheet, Printer, X } from 'lucide-react';
import { ApiError } from '../../apis/apiError';
import { statisticsApi } from '../../apis/statisticsApi';
import { FormAlert } from '../shared/FormFields';
import { formatDayLong } from './dashboardPeriod';

// UC-33: cac bao cao co the xuat, theo thu tu tab
const REPORTS = [
  { value: 'VISITS', label: 'Lượt khám' },
  { value: 'DOCTORS', label: 'Bác sĩ' },
  { value: 'DEPARTMENTS', label: 'Khoa / Phòng' },
  { value: 'PARACLINICAL', label: 'Cận lâm sàng' },
  { value: 'REVENUE', label: 'Doanh thu' },
  { value: 'DIAGNOSES', label: 'Bệnh thường gặp' },
];

// doi loi xuat file thanh thong bao
function exportError(error) {
  if (!(error instanceof ApiError)) return 'Không tạo được tệp. Vui lòng thử lại.';
  if (error.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') {
    return 'Khoảng thời gian hoặc lựa chọn không hợp lệ. Vui lòng kiểm tra lại.';
  }
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Bạn không có quyền xuất báo cáo.';
  if (error.status === undefined) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Không tạo được tệp. Vui lòng thử lại.';
}

// cho trinh duyet tai file ve
function saveFile({ blob, fileName }) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName || 'bao-cao-thong-ke.xlsx';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

// UC-33: hop thoai chon bao cao va tai file Excel
function ExportDialog({ api, period, groupBy, limit, onClose }) {
  const [selected, setSelected] = useState(() => new Set(REPORTS.map((r) => r.value)));
  const [bucket, setBucket] = useState(groupBy ?? 'DAY');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const toggle = (value) => setSelected((current) => {
    const next = new Set(current);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    return next;
  });
  const allSelected = selected.size === REPORTS.length;

  const download = async () => {
    setBusy(true);
    setError('');
    try {
      const file = await api.exportExcel({
        from: period.from,
        to: period.to,
        reports: REPORTS.map((r) => r.value).filter((value) => selected.has(value)),
        groupBy: bucket,
        limit,
      });
      saveFile(file);
      onClose();
    } catch (exportFailure) {
      setError(exportError(exportFailure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-export-title"
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-5"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="report-export-title" className="text-lg! font-bold! text-[#1b2533]">Xuất báo cáo Excel</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Từ {formatDayLong(period.from)} đến {formatDayLong(period.to)}, theo bộ lọc của trang.
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng" className="p-1 rounded-lg text-slate-400 hover:bg-slate-100 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        <fieldset className="space-y-2">
          <div className="flex items-center justify-between">
            <legend className="text-sm font-semibold text-slate-700">Báo cáo</legend>
            <button
              type="button"
              onClick={() => setSelected(new Set(allSelected ? [] : REPORTS.map((r) => r.value)))}
              className="text-xs font-medium text-[#127e7a] hover:underline cursor-pointer"
            >
              {allSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {REPORTS.map((option) => (
              <label key={option.value} className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selected.has(option.value)}
                  onChange={() => toggle(option.value)}
                  className="w-4 h-4 accent-[#127e7a]"
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="flex flex-col gap-1">
          <label htmlFor="report-export-group" className="text-sm font-semibold text-slate-700">
            Lượt khám và doanh thu
          </label>
          <select
            id="report-export-group"
            value={bucket}
            onChange={(event) => setBucket(event.target.value)}
            className="px-3 py-2 text-sm border border-slate-200 rounded-xl bg-white"
          >
            <option value="DAY">Theo ngày</option>
            <option value="MONTH">Theo tháng</option>
          </select>
        </div>

        {error && <FormAlert>{error}</FormAlert>}
        {selected.size === 0 && <p className="text-xs text-amber-700">Chọn ít nhất một báo cáo.</p>}

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={download}
            disabled={busy || selected.size === 0}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <FileSpreadsheet className="w-4 h-4" aria-hidden="true" />
            {busy ? 'Đang tạo tệp…' : 'Tải Excel'}
          </button>
        </div>
      </div>
    </div>
  );
}

// nut "Xuat Excel" va "In / Luu PDF" (in trang bang trinh duyet)
export function ReportActions({ api = statisticsApi, period, groupBy, limit, disabled }) {
  const [open, setOpen] = useState(false);
  const button = 'inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed';
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} disabled={disabled} className={button}>
        <FileSpreadsheet className="w-4 h-4" aria-hidden="true" />
        Xuất Excel
      </button>
      <button type="button" onClick={() => window.print()} disabled={disabled} className={button}>
        <Printer className="w-4 h-4" aria-hidden="true" />
        In / Lưu PDF
      </button>
      {open && (
        <ExportDialog
          api={api}
          period={period}
          groupBy={groupBy}
          limit={limit}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}

export default ReportActions;
