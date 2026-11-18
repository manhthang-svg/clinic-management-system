import { useState, useEffect, useRef } from 'react';
import { Search, RotateCcw, AlertCircle, Building2, Calendar, Activity, Filter, CheckCircle2 } from 'lucide-react';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';

/**
 * Khu vực Tìm kiếm & Bộ lọc (Search & Filter Bar) cho Screen #39.
 */
export function MedicalRecordsFilter({
  filters,
  onChangeFilter,
  onSearch,
  onReset,
  loading = false,
}) {
  const [rooms, setRooms] = useState([]);
  const [icdOptions, setIcdOptions] = useState([]);
  const [showIcdDropdown, setShowIcdDropdown] = useState(false);
  const [icdSearching, setIcdSearching] = useState(false);
  const icdRef = useRef(null);

  // Fetch phòng khám lâm sàng
  useEffect(() => {
    let ignore = false;
    async function loadRooms() {
      try {
        const data = await medicalRecordApi.getActiveRooms();
        if (!ignore && Array.isArray(data)) {
          setRooms(data);
        }
      } catch (err) {
        console.error('Không thể tải danh sách phòng khám:', err);
      }
    }
    loadRooms();
    return () => { ignore = true; };
  }, []);

  // Tìm kiếm ICD-10 gợi ý khi gõ
  useEffect(() => {
    let ignore = false;
    const timer = setTimeout(async () => {
      if (!filters.icd10 || filters.icd10.trim().length < 2) {
        if (!ignore) setIcdOptions([]);
        return;
      }

      setIcdSearching(true);
      try {
        const res = await medicalRecordApi.searchIcd10(filters.icd10.trim());
        if (!ignore && Array.isArray(res)) {
          setIcdOptions(res);
        }
      } catch {
        if (!ignore) setIcdOptions([]);
      } finally {
        if (!ignore) setIcdSearching(false);
      }
    }, 300);

    return () => {
      ignore = true;
      clearTimeout(timer);
    };
  }, [filters.icd10]);

  // Click outside để đóng dropdown ICD-10
  useEffect(() => {
    function handleClickOutside(e) {
      if (icdRef.current && !icdRef.current.contains(e.target)) {
        setShowIcdDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Validation ngày: từ ngày > đến ngày -> MSG50
  const dateError = Boolean(
    filters.from && filters.to && new Date(filters.from) > new Date(filters.to)
  );

  const handleSubmit = (e) => {
    e.preventDefault();
    if (dateError) return;
    onSearch();
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
      <form onSubmit={handleSubmit} noValidate>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          {/* 1. Ô tìm kiếm từ khóa */}
          <div className="xl:col-span-1">
            <label htmlFor="filter-keyword" className="block text-xs font-semibold text-slate-700 mb-1">
              Từ khóa tìm kiếm
            </label>
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
              <input
                id="filter-keyword"
                type="text"
                maxLength={100}
                value={filters.keyword}
                onChange={(e) => onChangeFilter('keyword', e.target.value)}
                placeholder="Mã BN, CCCD, Họ tên, SĐT..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a] text-slate-800 placeholder-slate-400"
              />
            </div>
            <span className="text-[11px] text-slate-400 mt-0.5 block">Tối đa 100 ký tự</span>
          </div>

          {/* 2. Từ ngày - Đến ngày */}
          <div>
            <label htmlFor="filter-from" className="block text-xs font-semibold text-slate-700 mb-1">
              Từ ngày
            </label>
            <input
              id="filter-from"
              type="date"
              value={filters.from}
              onChange={(e) => onChangeFilter('from', e.target.value)}
              className={`w-full px-3 py-2 text-sm border rounded-xl focus:outline-none focus:ring-2 text-slate-800 ${
                dateError
                  ? 'border-rose-400 focus:ring-rose-200 bg-rose-50/20'
                  : 'border-slate-200 focus:ring-[#127e7a]/30 focus:border-[#127e7a]'
              }`}
            />
          </div>

          <div>
            <label htmlFor="filter-to" className="block text-xs font-semibold text-slate-700 mb-1">
              Đến ngày
            </label>
            <input
              id="filter-to"
              type="date"
              value={filters.to}
              onChange={(e) => onChangeFilter('to', e.target.value)}
              className={`w-full px-3 py-2 text-sm border rounded-xl focus:outline-none focus:ring-2 text-slate-800 ${
                dateError
                  ? 'border-rose-400 focus:ring-rose-200 bg-rose-50/20'
                  : 'border-slate-200 focus:ring-[#127e7a]/30 focus:border-[#127e7a]'
              }`}
            />
          </div>

          {/* 3. Phòng khám */}
          <div>
            <label htmlFor="filter-room" className="block text-xs font-semibold text-slate-700 mb-1">
              Phòng khám
            </label>
            <select
              id="filter-room"
              value={filters.roomId}
              onChange={(e) => onChangeFilter('roomId', e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a] text-slate-800 bg-white"
            >
              <option value="ALL">Tất cả phòng khám</option>
              {rooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.roomName || room.name || room.roomCode}
                </option>
              ))}
            </select>
          </div>

          {/* 4. Chẩn đoán ICD-10 */}
          <div className="relative" ref={icdRef}>
            <label htmlFor="filter-icd" className="block text-xs font-semibold text-slate-700 mb-1">
              Chẩn đoán (ICD-10)
            </label>
            <input
              id="filter-icd"
              type="text"
              value={filters.icd10}
              onFocus={() => setShowIcdDropdown(true)}
              onChange={(e) => {
                onChangeFilter('icd10', e.target.value);
                setShowIcdDropdown(true);
              }}
              placeholder="Mã hoặc tên bệnh ICD-10"
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a] text-slate-800 placeholder-slate-400"
            />
            {/* Dropdown gợi ý ICD-10 */}
            {showIcdDropdown && icdOptions.length > 0 && (
              <ul className="absolute z-20 left-0 right-0 mt-1 max-h-48 overflow-y-auto rounded-xl border border-slate-200 bg-white py-1 shadow-lg text-xs">
                {icdOptions.map((item) => (
                  <li
                    key={item.maicd}
                    onClick={() => {
                      onChangeFilter('icd10', item.maicd);
                      setShowIcdDropdown(false);
                    }}
                    className="px-3 py-2 hover:bg-teal-50 cursor-pointer flex items-center justify-between"
                  >
                    <span className="font-mono font-bold text-[#127e7a]">{item.maicd}</span>
                    <span className="text-slate-700 truncate ml-2 text-right">{item.tenicd}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {/* Lỗi khi Từ ngày > Đến ngày */}
        {dateError && (
          <div className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-rose-600 bg-rose-50 px-3 py-2 rounded-xl border border-rose-200">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>Ngày bắt đầu không được sau ngày kết thúc.</span>
          </div>
        )}

        {/* Hàng 2: Trạng thái & Các nút thao tác */}
        <div className="mt-4 pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-slate-700">Trạng thái:</span>
            <select
              value={filters.status}
              onChange={(e) => onChangeFilter('status', e.target.value)}
              className="px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a] text-slate-800 bg-white"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="COMPLETED">Hoàn thành (Completed)</option>
              <option value="ABANDONED">Bỏ khám (Abandoned)</option>
              <option value="ACTIVE">Đang hoạt động (Active)</option>
            </select>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onReset}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl cursor-pointer transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Xóa bộ lọc
            </button>
            <button
              type="submit"
              disabled={loading || dateError}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] disabled:opacity-50 rounded-xl cursor-pointer shadow-xs transition-colors"
            >
              <Search className="w-3.5 h-3.5" />
              {loading ? 'Đang tìm…' : 'Tìm kiếm'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}

export default MedicalRecordsFilter;
