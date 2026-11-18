import { useState, useEffect, useCallback } from 'react';
import {
  FileCode2,
  Search,
  Filter,
  RefreshCw,
  Archive,
  Calendar,
  Clock,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  Download,
  AlertCircle,
  FileText,
  User,
} from 'lucide-react';
import { toast } from 'sonner';
import { AdminLayout } from '../../layouts';
import { recordArchiveApi } from '../../apis/recordArchiveApi';
import { AdminMedicalRecordExportModal } from './AdminMedicalRecordExportModal';

const RETENTION_CATEGORY_LABELS = {
  ALL: 'Tất cả loại lưu trữ',
  STANDARD: 'Tiêu chuẩn (10 năm)',
  CHRONIC: 'Bệnh mạn tính (15 năm)',
  RESEARCH: 'Nghiên cứu khoa học (20 năm)',
  PERMANENT: 'Lưu trữ vĩnh viễn',
};

export function RecordArchivePage({ currentAdmin, onLogout, onChangePassword }) {
  const [archives, setArchives] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [category, setCategory] = useState('ALL');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // Pagination
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Modal export
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [selectedArchive, setSelectedArchive] = useState(null);

  const fetchArchives = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await recordArchiveApi.searchArchives({
        keyword: searchKeyword || undefined,
        category: category !== 'ALL' ? category : undefined,
        from: fromDate || undefined,
        to: toDate || undefined,
        page,
        size: pageSize,
      });

      setArchives(result?.content || []);
      setTotalPages(result?.page?.totalPages || 1);
      setTotalElements(result?.page?.totalElements || 0);
    } catch (err) {
      console.error('Lỗi tải danh sách lưu trữ hồ sơ:', err);
      setError('Không thể tải danh sách lưu trữ hồ sơ bệnh án. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  }, [searchKeyword, category, fromDate, toDate, page, pageSize]);

  useEffect(() => {
    fetchArchives();
  }, [fetchArchives]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    setSearchKeyword(keyword);
  };

  const handleResetFilters = () => {
    setKeyword('');
    setSearchKeyword('');
    setCategory('ALL');
    setFromDate('');
    setToDate('');
    setPage(0);
  };

  const handleOpenExport = (archive) => {
    setSelectedArchive(archive);
    setExportModalOpen(true);
  };

  return (
    <AdminLayout
      currentAdmin={currentAdmin}
      onLogout={onLogout}
      onChangePassword={onChangePassword}
      activeKey="record-archive"
    >
      <div className="space-y-6 pb-12">
        {/* Page Header */}
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-5">
          <div className="space-y-1">
            <div className="flex items-center gap-2.5">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a]">
                <Archive className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl font-bold tracking-tight text-slate-900">
                  Lưu trữ Hồ sơ Bệnh án EMR
                </h1>
                <p className="text-xs text-slate-500">
                  Quản lý kho lưu trữ điện tử, kiểm soát tính toàn vẹn (SHA-256) và kết xuất dữ liệu theo Công văn 365/TTYQG
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={fetchArchives}
              disabled={loading}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-all shadow-2xs cursor-pointer"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-4 shadow-xs space-y-3">
          <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[240px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm theo số lưu trữ, số BA, mã BN, tên bệnh nhân, mã lượt khám..."
                className="w-full pl-9 pr-3 py-2 text-xs bg-slate-50/70 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
              />
            </div>

            <div className="min-w-[180px]">
              <select
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value);
                  setPage(0);
                }}
                className="w-full px-3 py-2 text-xs bg-slate-50/70 border border-slate-200 rounded-xl text-slate-800 focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
              >
                {Object.entries(RETENTION_CATEGORY_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-2 text-xs text-slate-600">
              <span className="text-slate-400">Hết hạn từ:</span>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  setPage(0);
                }}
                className="px-2.5 py-1.5 text-xs bg-slate-50/70 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
              />
              <span className="text-slate-400">đến:</span>
              <input
                type="date"
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  setPage(0);
                }}
                className="px-2.5 py-1.5 text-xs bg-slate-50/70 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
              />
            </div>

            <button
              type="submit"
              className="px-4 py-2 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all shadow-xs cursor-pointer"
            >
              Tìm kiếm
            </button>

            {(searchKeyword || category !== 'ALL' || fromDate || toDate) && (
              <button
                type="button"
                onClick={handleResetFilters}
                className="px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
              >
                Xóa lọc
              </button>
            )}
          </form>
        </div>

        {/* Content Table */}
        <div className="bg-white rounded-2xl border border-slate-200/90 shadow-xs overflow-hidden">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400 space-y-2">
              <RefreshCw className="w-8 h-8 animate-spin text-[#127e7a]" />
              <span className="text-xs">Đang tải kho hồ sơ lưu trữ...</span>
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center py-16 text-rose-500 space-y-2">
              <AlertCircle className="w-8 h-8" />
              <span className="text-sm font-semibold">{error}</span>
              <button
                type="button"
                onClick={fetchArchives}
                className="mt-2 px-3 py-1.5 text-xs font-semibold text-white bg-[#127e7a] rounded-lg"
              >
                Thử lại
              </button>
            </div>
          ) : archives.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400 space-y-2">
              <Archive className="w-10 h-10 stroke-1" />
              <span className="text-sm font-medium">Chưa có hồ sơ lưu trữ nào khớp với điều kiện tìm kiếm.</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-200 text-slate-600 font-bold uppercase tracking-wider text-[11px]">
                    <th className="py-3 px-4">Số lưu trữ</th>
                    <th className="py-3 px-4">Số hồ sơ / Lượt khám</th>
                    <th className="py-3 px-4">Bệnh nhân</th>
                    <th className="py-3 px-4">Thời điểm đóng & lưu trữ</th>
                    <th className="py-3 px-4">Thời hạn bảo quản</th>
                    <th className="py-3 px-4">Mã băm toàn vẹn (SHA-256)</th>
                    <th className="py-3 px-4 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-slate-700">
                  {archives.map((item) => (
                    <tr key={item.archiveId || item.soluutru} className="hover:bg-slate-50/70 transition-colors">
                      {/* Số lưu trữ */}
                      <td className="py-3 px-4 font-mono font-bold text-[#127e7a]">
                        {item.soluutru}
                      </td>

                      {/* Số hồ sơ / Mã lượt khám */}
                      <td className="py-3 px-4 space-y-0.5">
                        <div className="font-semibold text-slate-900">{item.soba}</div>
                        <div className="text-[11px] text-slate-500 font-mono">ĐK: {item.visitCode}</div>
                      </td>

                      {/* Bệnh nhân */}
                      <td className="py-3 px-4 space-y-0.5">
                        <div className="font-bold text-slate-900">{item.patientName}</div>
                        <div className="text-[11px] text-slate-500">
                          {item.patientCode} • {item.gender === 1 ? 'Nam' : item.gender === 2 ? 'Nữ' : 'Khác'}
                          {item.age !== null ? ` • ${item.age} tuổi` : ''}
                        </div>
                      </td>

                      {/* Thời điểm lưu trữ */}
                      <td className="py-3 px-4 space-y-0.5">
                        <div className="font-medium text-slate-800">
                          {item.archivedAt ? new Date(item.archivedAt).toLocaleDateString('vi-VN') : '—'}
                        </div>
                        <div className="text-[10px] text-slate-400">
                          {item.archivedAt ? new Date(item.archivedAt).toLocaleTimeString('vi-VN') : ''}
                        </div>
                      </td>

                      {/* Thời hạn bảo quản */}
                      <td className="py-3 px-4 space-y-0.5">
                        <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          {item.retentionYears ? `${item.retentionYears} năm` : 'Tiêu chuẩn'}
                        </span>
                        <div className="text-[11px] text-slate-500">
                          Hết hạn: <strong>{item.expiresOn ? new Date(item.expiresOn).toLocaleDateString('vi-VN') : '—'}</strong>
                        </div>
                      </td>

                      {/* SHA-256 */}
                      <td className="py-3 px-4 max-w-[200px]">
                        <span
                          title={item.packageSha256}
                          className="font-mono text-[11px] text-slate-600 bg-slate-100 px-2 py-1 rounded truncate block select-all"
                        >
                          {item.packageSha256
                            ? `${item.packageSha256.substring(0, 10)}...${item.packageSha256.substring(item.packageSha256.length - 8)}`
                            : '—'}
                        </span>
                      </td>

                      {/* Thao tác: Xuất dữ liệu điện tử CV 365 */}
                      <td className="py-3 px-4 text-center">
                        <button
                          type="button"
                          onClick={() => handleOpenExport(item)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-[#127e7a] hover:bg-[#0e6360] rounded-xl transition-all shadow-2xs cursor-pointer"
                          title="Kết xuất dữ liệu điện tử XML / JSON theo Công văn 365"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Kết xuất (XML/JSON)</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Phân trang */}
          {!loading && !error && archives.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-3 border-t border-slate-200 bg-slate-50/50 text-xs text-slate-600">
              <div>
                Hiển thị <strong>{archives.length}</strong> / <strong>{totalElements}</strong> hồ sơ lưu trữ
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="p-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span>
                  Trang <strong>{page + 1}</strong> / <strong>{totalPages}</strong>
                </span>
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="p-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Modal kết xuất EMR theo chuẩn Công văn 365 */}
        {selectedArchive && (
          <AdminMedicalRecordExportModal
            isOpen={exportModalOpen}
            onClose={() => {
              setExportModalOpen(false);
              setSelectedArchive(null);
            }}
            recordId={selectedArchive.medicalRecordId}
            recordNumber={selectedArchive.soba}
            visitCode={selectedArchive.visitCode}
          />
        )}
      </div>
    </AdminLayout>
  );
}

export default RecordArchivePage;

