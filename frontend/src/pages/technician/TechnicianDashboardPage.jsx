import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import {
  Building2,
  Clock,
  ArrowRight,
  RefreshCw,
  MapPin,
  AlertTriangle,
  Microscope,
  CheckCircle2,
  SkipForward,
  ListOrdered,
  CalendarDays,
  Eye,
} from 'lucide-react';
import { toast } from 'sonner';

import { MetricCard } from '../../components/shared/MetricCard';

/**
 * Mock data cho phòng máy và thông tin KTV
 */
const MOCK_ROOMS = [
  {
    id: 'room-1',
    code: 'P.204',
    name: 'Phòng Xét nghiệm Sinh hóa',
    department: 'Khoa Cận lâm sàng & Xét nghiệm',
    location: 'Tầng 2, Nhà B (P.204)',
    activeTech: 'KTV. Nguyễn Văn Bảo',
    isAssigned: true,
  },
  {
    id: 'room-2',
    code: 'P.205',
    name: 'Phòng Xét nghiệm Huyết học',
    department: 'Khoa Cận lâm sàng & Xét nghiệm',
    location: 'Tầng 2, Nhà B (P.205)',
    activeTech: 'KTV. Trần Minh Đức',
    isAssigned: false,
  },
  {
    id: 'room-3',
    code: 'P.105',
    name: 'Phòng Siêu âm Tổng quát 01',
    department: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tầng 1, Nhà A (P.105)',
    activeTech: 'KTV. Lê Thị Hoa',
    isAssigned: false,
  },
];

/**
 * Mock data top 5 bệnh nhân đứng đầu hàng đợi
 */
const INITIAL_NEXT_QUEUE = [
  {
    id: 'q-1',
    queueNumber: 1,
    patientName: 'nguyễn văn phi',
    patientId: 'BN6013016',
    orderDetailId: 'CD0000000102',
    age: 34,
    gender: 'Nam',
    insuranceType: 'SERVICE',
    priority: 'NORMAL',
    diagnosis: 'Bệnh béo phì quá mức với giảm thông khí phế nang',
    serviceName: 'Bộ đường huyết (Glucose, HbA1c)',
    extraServicesCount: 2,
    doctorName: 'BSCKII Trần Văn An',
    doctorRoom: 'PK Nội tổng hợp 1',
    orderTime: '16:47',
    waitingMinutes: 15,
  },
  {
    id: 'q-2',
    queueNumber: 2,
    patientName: 'Trần Thị Mai',
    patientId: 'BN8821903',
    orderDetailId: 'CD0000000101',
    age: 68,
    gender: 'Nữ',
    insuranceType: 'BHYT',
    priority: 'PRIORITY',
    priorityReason: 'Người cao tuổi (≥ 60t)',
    diagnosis: 'Tăng huyết áp vô căn, theo dõi biến chứng thận',
    serviceName: 'Định lượng Ure, Creatinin máu',
    extraServicesCount: 1,
    doctorName: 'ThS.BS Lê Hồng Sơn',
    doctorRoom: 'PK Tim mạch',
    orderTime: '16:50',
    waitingMinutes: 12,
  },
  {
    id: 'q-3',
    queueNumber: 3,
    patientName: 'Vũ Hải Đăng',
    patientId: 'BN4019281',
    orderDetailId: 'CD0000000099',
    age: 8,
    gender: 'Nam',
    insuranceType: 'BHYT',
    priority: 'PRIORITY',
    priorityReason: 'Trẻ em dưới 10 tuổi',
    diagnosis: 'Sốt phát ban nghi do virus, theo dõi tiểu cầu',
    serviceName: 'Tổng phân tích tế bào máu ngoại vi (bằng máy đếm laser)',
    extraServicesCount: 0,
    doctorName: 'BSCKI Phạm Thị Lan',
    doctorRoom: 'PK Nhi',
    orderTime: '16:53',
    waitingMinutes: 9,
  },
  {
    id: 'q-4',
    queueNumber: 4,
    patientName: 'Đặng Thu Thảo',
    patientId: 'BN9012374',
    orderDetailId: 'CD0000000098',
    age: 29,
    gender: 'Nữ',
    insuranceType: 'SERVICE',
    priority: 'NORMAL',
    diagnosis: 'Bướu nhân tuyến giáp hai thùy Tirads 3',
    serviceName: 'Xét nghiệm chức năng tuyến giáp (FT3, FT4, TSH)',
    extraServicesCount: 2,
    doctorName: 'BSCKII Trần Văn An',
    doctorRoom: 'PK Nội tổng hợp 1',
    orderTime: '16:56',
    waitingMinutes: 6,
  },
  {
    id: 'q-5',
    queueNumber: 5,
    patientName: 'Lê Hoàng Long',
    patientId: 'BN3301982',
    orderDetailId: 'CD0000000095',
    age: 45,
    gender: 'Nam',
    insuranceType: 'BHYT',
    priority: 'NORMAL',
    diagnosis: 'Viêm gan B mạn tính, theo dõi men gan tăng',
    serviceName: 'Đo hoạt độ AST, ALT (Men gan)',
    extraServicesCount: 1,
    doctorName: 'ThS.BS Nguyễn Minh Tuấn',
    doctorRoom: 'PK Tiêu hóa',
    orderTime: '17:00',
    waitingMinutes: 3,
  },
];

/**
 * Mock data top 5 kết quả đang chờ nhập hoặc lưu nháp của KTV
 */
const INITIAL_PENDING_RESULTS = [
  {
    id: 'res-1',
    orderDetailId: 'CD0000000103',
    patientName: 'lê văn tê',
    patientId: 'BN9832270',
    age: 28,
    gender: 'Nam',
    insuranceType: 'SERVICE',
    diagnosis: 'Tê và/hoặc mất cảm giác phân ly chi dưới',
    serviceName: 'Bộ đường huyết (Glucose, HbA1c)',
    performedAtTime: '17:09',
    completedMinutesAgo: 42,
    status: 'DRAFT',
    statusLabel: 'Bản nháp',
    savedDraftTime: '17:30',
    doctorName: 'BSCKII Trần Văn An',
    doctorRoom: 'PK Nội tổng hợp 1',
  },
  {
    id: 'res-2',
    orderDetailId: 'CD0000000104',
    patientName: 'Hoàng Đức Thịnh',
    patientId: 'BN7129031',
    age: 52,
    gender: 'Nam',
    insuranceType: 'BHYT',
    diagnosis: 'Rối loạn lipid máu hỗn hợp, vữa xơ động mạch',
    serviceName: 'Bộ mỡ máu (Cholesterol, Triglycerid, HDL-C, LDL-C)',
    performedAtTime: '17:15',
    completedMinutesAgo: 36,
    status: 'PENDING',
    statusLabel: 'Chưa nhập',
    doctorName: 'ThS.BS Lê Hồng Sơn',
    doctorRoom: 'PK Tim mạch',
  },
  {
    id: 'res-3',
    orderDetailId: 'CD0000000105',
    patientName: 'Phạm Ngọc Lan',
    patientId: 'BN5102934',
    age: 31,
    gender: 'Nữ',
    insuranceType: 'SERVICE',
    diagnosis: 'Nhiễm khuẩn tiết niệu, tiểu buốt rắt',
    serviceName: 'Tổng phân tích nước tiểu (10 thông số)',
    performedAtTime: '17:22',
    completedMinutesAgo: 29,
    status: 'DRAFT',
    statusLabel: 'Bản nháp',
    savedDraftTime: '17:35',
    doctorName: 'BSCKI Đỗ Hải Nam',
    doctorRoom: 'PK Ngoại tiết niệu',
  },
  {
    id: 'res-4',
    orderDetailId: 'CD0000000106',
    patientName: 'Ngô Gia Huy',
    patientId: 'BN9120485',
    age: 22,
    gender: 'Nam',
    insuranceType: 'BHYT',
    diagnosis: 'Tiêu chảy cấp mất nước độ I, theo dõi hạ kali máu',
    serviceName: 'Điện giải đồ (Na+, K+, Cl-)',
    performedAtTime: '17:32',
    completedMinutesAgo: 19,
    status: 'PENDING',
    statusLabel: 'Chưa nhập',
    doctorName: 'BSCKII Trần Văn An',
    doctorRoom: 'PK Nội tổng hợp 1',
  },
  {
    id: 'res-5',
    orderDetailId: 'CD0000000107',
    patientName: 'Bùi Ánh Tuyết',
    patientId: 'BN6629104',
    age: 60,
    gender: 'Nữ',
    insuranceType: 'BHYT',
    diagnosis: 'Gout mạn tính có tophi khớp ngón chân cái',
    serviceName: 'Đo nồng độ Acid Uric huyết thanh',
    performedAtTime: '17:41',
    completedMinutesAgo: 10,
    status: 'PENDING',
    statusLabel: 'Chưa nhập',
    doctorName: 'ThS.BS Nguyễn Minh Tuấn',
    doctorRoom: 'PK Cơ xương khớp',
  },
];

/**
 * Mock data Lịch trực cá nhân sắp tới (BR-55)
 */
const MOCK_UPCOMING_SHIFTS = [
  {
    dateStr: 'Hôm nay, 13/11',
    shiftName: 'Ca Chiều',
    time: '13:00 – 17:30',
    room: 'P.204 – Sinh hóa',
    status: 'CURRENT',
    statusLabel: 'Đang diễn ra',
  },
  {
    dateStr: 'Ngày mai, 14/11',
    shiftName: 'Ca Sáng',
    time: '07:30 – 12:00',
    room: 'P.204 – Sinh hóa',
    status: 'UPCOMING',
    statusLabel: 'Sắp tới',
  },
  {
    dateStr: 'Thứ Bảy, 15/11',
    shiftName: 'Ca Sáng',
    time: '07:30 – 12:00',
    room: 'P.205 – Huyết học',
    status: 'UPCOMING',
    statusLabel: 'Đổi phòng trực',
  },
  {
    dateStr: 'Chủ Nhật, 16/11',
    shiftName: 'Nghỉ ca',
    time: '—',
    room: 'Không phân công',
    status: 'OFF',
    statusLabel: 'Nghỉ ca',
  },
];

export function TechnicianDashboardPage() {
  const navigate = useNavigate();

  // 1. Quản lý phòng làm việc hiện tại
  const [currentRoomId] = useState(MOCK_ROOMS[0].id);
  const currentRoom = MOCK_ROOMS.find((r) => r.id === currentRoomId) || MOCK_ROOMS[0];

  // Trạng thái ca trực (mặc định đang trong ca chiều 13:00 - 17:30)
  const [isInShift, setIsInShift] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastRefreshedAt, setLastRefreshedAt] = useState(new Date());

  // 2. Dữ liệu tác nghiệp (Chỉ hiển thị để theo dõi)
  const nextQueueList = INITIAL_NEXT_QUEUE;
  const pendingResultsList = INITIAL_PENDING_RESULTS;

  // Tự động làm mới dữ liệu mỗi 10 giây (UI-04)
  useEffect(() => {
    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        setLastRefreshedAt(new Date());
      }
    }, 10_000);

    return () => clearInterval(timer);
  }, []);

  // Làm mới thủ công
  const handleManualRefresh = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setLastRefreshedAt(new Date());
      setIsRefreshing(false);
      toast.info('Đã cập nhật dữ liệu tổng quan mới nhất.');
    }, 400);
  };

  return (
    <div className="space-y-6 w-full pb-10">
      {/* =========================================================================
          1. THANH TRẠNG THÁI CA TRỰC & PHÒNG LÀM VIỆC (HEADER BANNER)
          Bám sát BR-05, BR-19, BR-55
         ========================================================================= */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-5">
        <div className="space-y-2">
          {/* Tag Chuyên khoa & Mã phòng */}
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-teal-50 text-[#0d9488] border border-teal-200/80">
              Phòng: {currentRoom.code}
            </span>
            <span className="text-xs font-semibold text-slate-500">
              {currentRoom.department}
            </span>
            {isInShift ? (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 shadow-2xs">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                Đang trong ca trực
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200 shadow-2xs">
                <span className="w-2 h-2 rounded-full bg-amber-500" />
                Ngoài ca trực (Chỉ xem)
              </span>
            )}
          </div>

          {/* Tên phòng & Vị trí */}
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight flex items-center gap-2.5">
              <span>{currentRoom.name}</span>
            </h1>
            <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 mt-1 font-medium">
              <span className="flex items-center gap-1 text-slate-700 font-semibold">
                <MapPin className="w-3.5 h-3.5 text-slate-400" />
                {currentRoom.location}
              </span>
              <span>•</span>
              <span className="flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 text-slate-400" />
                Ca hiện tại: <strong className="text-slate-700">Ca Chiều (13:00 – 17:30)</strong>
              </span>
              <span>•</span>
              <span>
                Phụ trách: <strong className="text-slate-700">{currentRoom?.activeTech || 'Chưa có'}</strong>
              </span>
            </div>
          </div>
        </div>

        {/* Nút làm mới & Bộ chuyển đổi */}
        <div className="flex flex-wrap sm:flex-nowrap items-center gap-3 shrink-0">
          {/* Nút làm mới nhanh */}
          <button
            type="button"
            onClick={handleManualRefresh}
            disabled={isRefreshing}
            className="h-11 w-11 flex items-center justify-center rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-900 shadow-2xs transition-all cursor-pointer disabled:opacity-50"
            title={`Làm mới dữ liệu (Tự động mỗi 10s • Lần cuối: ${lastRefreshedAt.toLocaleTimeString('vi-VN')})`}
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-[#0d9488]' : ''}`} />
          </button>

          {/* Nút Toggle trạng thái ca trực (Demo chuyển đổi ca) */}
          <button
            type="button"
            onClick={() => {
              setIsInShift((prev) => !prev);
              toast.info(
                !isInShift
                  ? 'Đã chuyển sang trạng thái: Đang trong ca trực'
                  : 'Đã chuyển sang trạng thái: Ngoài ca trực (MSG48: Chế độ chỉ xem)'
              );
            }}
            className="h-11 px-3 inline-flex items-center gap-1 text-[11px] font-semibold text-slate-500 hover:text-slate-800 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl transition-all cursor-pointer"
            title="Bấm để test giả lập đổi trạng thái Trong ca / Ngoài ca"
          >
            <Eye className="w-3.5 h-3.5" />
            <span className="hidden md:inline">{isInShift ? 'Mô phỏng ngoài ca' : 'Mô phỏng trong ca'}</span>
          </button>
        </div>
      </div>

      {/* Cảnh báo ngoài ca trực nếu isInShift = false */}
      {!isInShift && (
        <div className="flex items-center gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 font-medium shadow-2xs">
          <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
          <span>
            <strong>Thông báo ngoài ca trực (MSG48):</strong> Bạn hiện đang ngoài ca trực hoặc ở chế độ chỉ xem. Các thao tác tiếp nhận bệnh nhân, gọi số hoặc gửi kết quả sẽ bị hạn chế.
          </span>
        </div>
      )}

      {/* =========================================================================
          2. HÀNG THẺ CHỈ SỐ TỔNG QUAN TRONG NGÀY (4 KPI CARDS)
          Bấm vào từng thẻ sẽ chuyển thẳng sang tab/màn hình tương ứng
         ========================================================================= */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1: Đang chờ thực hiện (Waiting) */}
        <div
          onClick={() => navigate('/technician/queue')}
          className="cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0"
        >
          <MetricCard
            title="Đang chờ thực hiện"
            value="12"
            icon={Clock}
            iconBg="bg-teal-50 text-[#0d9488]"
            valueColor="text-teal-900"
            subtitle="Đã có STT chờ gọi vào phòng"
            dotColor="bg-teal-500"
            footerLabel="Mức ưu tiên"
            footerValue="2 ca ưu tiên cao"
            footerValueColor="text-amber-700 font-bold"
          />
        </div>

        {/* KPI 2: Đã bỏ qua (Skipped - UC-45/UC-46) */}
        <div
          onClick={() => navigate('/technician/queue', { state: { tab: 'skipped' } })}
          className="cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0"
        >
          <MetricCard
            title="Đã bỏ qua hôm nay"
            value="3"
            icon={SkipForward}
            iconBg="bg-amber-50 text-amber-700"
            valueColor="text-amber-900"
            subtitle="Vắng mặt khi gọi lượt"
            dotColor="bg-amber-500"
            footerLabel="Chờ gọi lại"
            footerValue="3 bệnh nhân"
            footerValueColor="text-slate-700"
          />
        </div>

        {/* KPI 3: Chờ nhập kết quả (Pending Result - UC-64) */}
        <div
          onClick={() => navigate('/technician/results')}
          className="cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0"
        >
          <MetricCard
            title="Chờ nhập kết quả"
            value="5"
            icon={Microscope}
            iconBg="bg-sky-50 text-sky-700"
            valueColor="text-sky-900"
            subtitle="Đã làm xong, chưa gửi KQ"
            dotColor="bg-sky-500"
            footerLabel="Cần xử lý sớm"
            footerValue="1 ca >30 phút"
            footerValueColor="text-rose-600 font-bold"
          />
        </div>

        {/* KPI 4: Đã hoàn thành hôm nay (Completed) */}
        <div
          onClick={() => navigate('/technician/results')}
          className="cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0"
        >
          <MetricCard
            title="Đã hoàn thành hôm nay"
            value="28"
            icon={CheckCircle2}
            iconBg="bg-emerald-50 text-emerald-700"
            valueColor="text-emerald-900"
            subtitle="Phiếu kết quả đã gửi bác sĩ"
            dotColor="bg-emerald-500"
            footerLabel="Tiến độ trả KQ"
            footerValue="100% đúng hạn"
            footerValueColor="text-emerald-700 font-bold"
          />
        </div>
      </div>

      {/* =========================================================================
          3. KHU VỰC TÁC NGHIỆP ƯU TIÊN (CHIA 2 CỘT TRỰC QUAN)
         ========================================================================= */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* -----------------------------------------------------------------------
            CỘT TRÁI — BỆNH NHÂN SẮP TỚI LƯỢT (NEXT IN QUEUE)
           ----------------------------------------------------------------------- */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between space-y-4">
          <div className="space-y-4">
            {/* Header Cột Trái */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                  <ListOrdered className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-sm font-extrabold text-slate-900">
                    Bệnh nhân sắp tới lượt
                  </h2>
                  <p className="text-[11px] text-slate-500">
                    Top 5 bệnh nhân đứng đầu hàng đợi phòng {currentRoom.code}
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => navigate('/technician/queue')}
                className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group"
              >
                <span>Xem tất cả (12)</span>
                <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
              </button>
            </div>

            {/* Danh sách 5 bệnh nhân tiếp theo */}
            <div className="divide-y divide-slate-100">
              {nextQueueList.map((item) => {
                const isPriority = item.priority === 'PRIORITY';
                const numStr = item.queueNumber < 10 ? `0${item.queueNumber}` : item.queueNumber;

                return (
                  <div
                    key={item.id}
                    onClick={() => navigate('/technician/queue')}
                    className="py-3 px-2.5 rounded-xl hover:bg-slate-50/90 transition-all cursor-pointer group space-y-1.5"
                    title="Nhấp để chuyển sang màn hình Hàng đợi tác nghiệp"
                  >
                    {/* Dòng 1: STT, Tên BN, Mã BN, Tuổi/Giới tính, Đối tượng khám & Trạng thái */}
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0 flex-wrap">
                        <span className="font-extrabold text-teal-800 bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200/80 text-xs shrink-0 font-mono">
                          #{numStr}
                        </span>
                        <span className="text-xs font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors truncate">
                          {item.patientName}
                        </span>
                        <span className="font-mono text-[11px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded shrink-0">
                          {item.patientId}
                        </span>
                        <span className="text-[11px] text-slate-400 shrink-0">
                          {item.age}t • {item.gender}
                        </span>
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-semibold shrink-0 ${
                            item.insuranceType === 'BHYT'
                              ? 'bg-blue-50 text-blue-700 border border-blue-200'
                              : 'bg-slate-100 text-slate-600 border border-slate-200'
                          }`}
                        >
                          {item.insuranceType === 'BHYT' ? 'BHYT' : 'Dịch vụ'}
                        </span>
                        {isPriority && (
                          <span
                            className="px-1.5 py-0.5 rounded text-[10px] font-extrabold bg-amber-50 text-amber-700 border border-amber-200 shrink-0"
                            title={item.priorityReason}
                          >
                            Ưu tiên
                          </span>
                        )}
                      </div>

                      {/* Trạng thái chờ gọi & Thời gian chờ */}
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
                          <span className="w-1.5 h-1.5 rounded-full bg-teal-500 animate-pulse" />
                          Đang chờ gọi
                        </span>
                        <span className="text-[11px] font-medium text-slate-500 shrink-0 hidden sm:inline-block">
                          Chờ {item.waitingMinutes}p
                        </span>
                      </div>
                    </div>

                    {/* Dòng 2: Tên dịch vụ cận lâm sàng & Thời gian chỉ định */}
                    <div className="flex items-center justify-between text-xs gap-2">
                      <div className="text-slate-800 font-medium truncate">
                        <span>{item.serviceName}</span>
                        {item.extraServicesCount > 0 && (
                          <span className="ml-1.5 text-[10px] font-bold text-[#0d9488] bg-teal-50 px-1.5 py-0.5 rounded">
                            +{item.extraServicesCount} DV khác
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-slate-400 shrink-0 flex items-center gap-1">
                        <Clock className="w-3 h-3 text-slate-400" />
                        <span>{item.orderTime}</span>
                      </div>
                    </div>

                    {/* Dòng 3: Bác sĩ chỉ định & Chẩn đoán sơ bộ */}
                    <div className="flex items-center justify-between text-[11px] text-slate-500 gap-2 pt-0.5">
                      <span className="truncate">
                        <strong className="text-slate-600 font-semibold">BS:</strong> {item.doctorName} ({item.doctorRoom})
                      </span>
                      {item.diagnosis && (
                        <span className="text-slate-400 italic truncate max-w-[45%] text-right hidden sm:inline-block" title={item.diagnosis}>
                          CĐ: {item.diagnosis}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
            <span>* Sắp xếp: Ưu tiên trước, theo thứ tự STT tăng dần (Chế độ xem tổng quan)</span>
            <span className="text-teal-700 font-semibold cursor-pointer hover:underline" onClick={() => navigate('/technician/queue')}>
              Vào hàng đợi để tiếp nhận →
            </span>
          </div>
        </div>

        {/* -----------------------------------------------------------------------
            CỘT PHẢI — KẾT QUẢ CỦA TÔI ĐANG CHỜ NHẬP / BẢN NHÁP (UC-64)
           ----------------------------------------------------------------------- */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between space-y-4">
          <div className="space-y-4">
            {/* Header Cột Phải */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-700 flex items-center justify-center font-bold">
                  <Microscope className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-sm font-extrabold text-slate-900">
                    Kết quả đang chờ nhập / Bản nháp
                  </h2>
                  <p className="text-[11px] text-slate-500">
                    Chỉ định đã thực hiện cần gửi kết quả (xếp cũ nhất lên trước)
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => navigate('/technician/results')}
                className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group"
              >
                <span>Xem tất cả (5)</span>
                <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
              </button>
            </div>

            {/* Danh sách 5 kết quả đang chờ nhập */}
            <div className="divide-y divide-slate-100">
              {pendingResultsList.map((item) => {
                const isDraft = item.status === 'DRAFT';
                const isOverdue = item.completedMinutesAgo >= 30;

                return (
                  <div
                    key={item.id}
                    onClick={() => navigate('/technician/results')}
                    className="py-3 px-2.5 rounded-xl hover:bg-slate-50/90 transition-all cursor-pointer group space-y-1.5"
                    title="Nhấp để chuyển sang màn hình Nhập kết quả"
                  >
                    {/* Dòng 1: Tên BN, Mã BN, Tuổi/Giới tính, Đối tượng khám, Badge trạng thái & Cảnh báo quá hạn */}
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0 flex-wrap">
                        <span className="text-xs font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors truncate">
                          {item.patientName}
                        </span>
                        <span className="font-mono text-[11px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded shrink-0">
                          {item.patientId}
                        </span>
                        <span className="text-[11px] text-slate-400 shrink-0">
                          {item.age}t • {item.gender}
                        </span>
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-semibold shrink-0 ${
                            item.insuranceType === 'BHYT'
                              ? 'bg-blue-50 text-blue-700 border border-blue-200'
                              : 'bg-slate-100 text-slate-600 border border-slate-200'
                          }`}
                        >
                          {item.insuranceType === 'BHYT' ? 'BHYT' : 'Dịch vụ'}
                        </span>
                        {/* Badge trạng thái */}
                        {isDraft ? (
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-sky-50 text-sky-700 border border-sky-200 shrink-0">
                            Bản nháp ({item.savedDraftTime})
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 shrink-0">
                            Chưa nhập
                          </span>
                        )}
                        {isOverdue && (
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-extrabold bg-rose-50 text-rose-700 border border-rose-200 shrink-0">
                            &gt;30p
                          </span>
                        )}
                      </div>

                      {/* Thời gian hoàn tất & Thời gian chờ gửi */}
                      <div className="text-right shrink-0">
                        <span className="text-[11px] font-semibold text-slate-700">
                          Xong lúc {item.performedAtTime}
                        </span>
                        <span className="text-[10px] text-slate-400 block">
                          Chờ gửi {item.completedMinutesAgo}p
                        </span>
                      </div>
                    </div>

                    {/* Dòng 2: Tên dịch vụ & Mã phiếu */}
                    <div className="flex items-center justify-between text-xs gap-2">
                      <div className="text-slate-800 font-medium truncate">
                        <span>{item.serviceName}</span>
                      </div>
                      <span className="font-mono text-[11px] text-slate-400 shrink-0">
                        {item.orderDetailId}
                      </span>
                    </div>

                    {/* Dòng 3: Bác sĩ chỉ định & Chẩn đoán */}
                    <div className="flex items-center justify-between text-[11px] text-slate-500 gap-2 pt-0.5">
                      <span className="truncate">
                        <strong className="text-slate-600 font-semibold">BS:</strong> {item.doctorName} ({item.doctorRoom})
                      </span>
                      {item.diagnosis && (
                        <span className="text-slate-400 italic truncate max-w-[45%] text-right hidden sm:inline-block" title={item.diagnosis}>
                          CĐ: {item.diagnosis}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
            <span>* Sắp xếp: Ưu tiên ca chờ lâu nhất lên trước (Chế độ xem tổng quan)</span>
            <span className="text-teal-700 font-semibold cursor-pointer hover:underline" onClick={() => navigate('/technician/results')}>
              Vào danh sách để nhập KQ →
            </span>
          </div>
        </div>
      </div>

      {/* =========================================================================
          4. KHỐI LỊCH TRỰC CÁ NHÂN SẮP TỚI (MINI SHIFT SCHEDULE - BR-55)
         ========================================================================= */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
              <CalendarDays className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-slate-900">
                Lịch trực cá nhân sắp tới
              </h2>
              <p className="text-[11px] text-slate-500">
                Phân công ca trực của bạn trong 4 ngày tới (theo phân bổ của Quản trị viên)
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => navigate('/technician/schedules')}
            className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group"
          >
            <span>Xem toàn bộ lịch trực</span>
            <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
          </button>
        </div>

        {/* Grid 4 ngày lịch trực */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
          {MOCK_UPCOMING_SHIFTS.map((shift, idx) => {
            const isCurrent = shift.status === 'CURRENT';
            const isOff = shift.status === 'OFF';

            return (
              <div
                key={idx}
                className={`p-3.5 rounded-xl border transition-all ${
                  isCurrent
                    ? 'bg-teal-50/60 border-teal-200/90 shadow-2xs ring-1 ring-teal-500/20'
                    : isOff
                    ? 'bg-slate-50/60 border-slate-200 text-slate-400'
                    : 'bg-white border-slate-200/80 hover:border-teal-200 hover:shadow-2xs'
                }`}
              >
                {/* Ngày & Trạng thái */}
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-xs font-bold ${isCurrent ? 'text-teal-900' : 'text-slate-800'}`}>
                    {shift.dateStr}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-extrabold ${
                      isCurrent
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : isOff
                        ? 'bg-slate-200/70 text-slate-600'
                        : 'bg-teal-50 text-teal-700 border border-teal-200'
                    }`}
                  >
                    {shift.statusLabel}
                  </span>
                </div>

                {/* Ca trực & Khung giờ */}
                <div className="space-y-1">
                  <div className={`text-sm font-extrabold ${isOff ? 'text-slate-400' : 'text-slate-900'}`}>
                    {shift.shiftName}
                  </div>
                  <div className="text-[11px] text-slate-500 flex items-center gap-1">
                    <Clock className="w-3 h-3 text-slate-400" />
                    <span>{shift.time}</span>
                  </div>
                  <div className="text-[11px] font-semibold text-slate-700 flex items-center gap-1 pt-1">
                    <Building2 className="w-3 h-3 text-slate-400" />
                    <span>{shift.room}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default TechnicianDashboardPage;
