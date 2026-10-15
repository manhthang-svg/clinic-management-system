import { useState, type FormEvent } from 'react'
import {
  User,
  Lock,
  Eye,
  EyeOff,
  ShieldCheck,
  CheckCircle2,
  Users,
  Stethoscope,
  Building2,
  ArrowRight,
  Activity,
} from 'lucide-react'

import { ApiError } from '../../shared/api/api-error'
import { Alert, AlertDescription, Badge, Button, Input, Label } from '../../components/ui'

interface LoginPageProps {
  readonly onLogin: (username: string, password: string) => Promise<void>
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Tên đăng nhập hoặc mật khẩu không đúng.'
  }
  return 'Không thể đăng nhập lúc này. Vui lòng thử lại.'
}

/** UC-1 Login with Hospital Management Design System matching reference design. */
export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [rememberMe, setRememberMe] = useState(true)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onLogin(username.trim(), password)
    } catch (cause) {
      setError(errorMessage(cause))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen w-full flex bg-[#f8fafc] font-['Plus_Jakarta_Sans',sans-serif]">
      <div className="flex w-full min-h-screen">
        {/* Left Side: 50% width, form centered */}
        <section className="w-full lg:w-1/2 flex-none flex flex-col justify-between p-6 sm:p-8 lg:p-10 xl:p-12 bg-white min-h-screen z-10 shadow-[4px_0_24px_rgba(20,37,52,0.03)]">
          {/* Brand Header */}
          <header className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-[#ecfdf5] text-[#0d9488] flex items-center justify-center border border-[#a7f3d0]/60 shadow-xs">
                <svg
                  width="22"
                  height="22"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <circle cx="12" cy="12" r="9" strokeWidth="1.8" />
                  <path d="M12 8v8M8 12h8" strokeWidth="2.2" />
                </svg>
              </div>
              <div>
                <span className="text-lg font-bold text-slate-800 tracking-tight block leading-tight">
                  METPH
                </span>
                <span className="text-[11px] font-semibold text-slate-400 tracking-wider uppercase block">
                  Bệnh viện Đa khoa
                </span>
              </div>
            </div>

            <Badge
              variant="mint"
              className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#ecfdf5] border border-[#a7f3d0]/60 text-xs font-medium text-[#059669]"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-[#10b981]" />
              <span>Cổng Y Tế Ngoại Trú</span>
            </Badge>
          </header>

          {/* Center Form Card - Centered within the 50% left section */}
          <div className="w-full max-w-[420px] mx-auto my-auto py-6">
            <div className="mb-6">
              <h1 className="text-2xl sm:text-[28px] font-bold text-slate-800 tracking-tight mb-2">
                Đăng nhập
              </h1>
              <p className="text-sm text-slate-500 leading-relaxed">
                Vui lòng nhập thông tin nhân viên để tiếp tục làm việc trong ca trực.
              </p>
            </div>

            {/* Quick Role Hint Pills */}
            <div className="flex flex-wrap items-center gap-2 mb-6">
              <span className="text-xs text-slate-400 font-medium mr-1">Vai trò:</span>
              {[
                { name: 'Bác sĩ', user: 'dr.an' },
                { name: 'Lễ tân', user: 'receptionist' },
                { name: 'Thu ngân', user: 'cashier' },
                { name: 'Quản trị', user: 'admin' },
              ].map((role) => (
                <button
                  key={role.name}
                  type="button"
                  onClick={() => setUsername(role.user)}
                  className={`px-3 py-1 rounded-full text-xs font-medium transition-all cursor-pointer ${
                    username === role.user
                      ? 'bg-[#ecfdf5] text-[#059669] border border-[#a7f3d0] shadow-xs'
                      : 'bg-slate-100/90 text-slate-600 border border-slate-200/80 hover:bg-slate-200/80 hover:text-slate-800'
                  }`}
                  title={`Chọn tài khoản ${role.name}`}
                >
                  {role.name}
                </button>
              ))}
            </div>

            <form className="space-y-4" onSubmit={handleSubmit} noValidate>
              <div className="space-y-1.5">
                <Label htmlFor="login-username" className="text-xs font-medium text-slate-700">
                  Tên đăng nhập
                </Label>
                <Input
                  id="login-username"
                  name="username"
                  autoComplete="username"
                  placeholder="Nhập mã nhân viên hoặc username..."
                  required
                  maxLength={100}
                  value={username}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setUsername(event.target.value)}
                  leadingIcon={<User className="w-4 h-4 text-slate-400" />}
                />
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <Label htmlFor="login-password" className="text-xs font-medium text-slate-700">
                    Mật khẩu
                  </Label>
                  <a
                    href="#forgot"
                    onClick={(e) => {
                      e.preventDefault()
                      alert('Vui lòng liên hệ phòng Công nghệ Thông tin (IT) Bệnh viện để được hỗ trợ cấp lại mật khẩu.')
                    }}
                    className="text-xs font-medium text-[#0d9488] hover:text-[#0f766e] hover:underline transition-colors cursor-pointer"
                  >
                    Quên mật khẩu?
                  </a>
                </div>
                <Input
                  id="login-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu..."
                  required
                  maxLength={200}
                  value={password}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setPassword(event.target.value)}
                  leadingIcon={<Lock className="w-4 h-4 text-slate-400" />}
                  trailingIcon={
                    <button
                      type="button"
                      onClick={() => setShowPassword((prev) => !prev)}
                      className="p-1 text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  }
                />
              </div>

              <div className="flex items-center justify-between pt-1">
                <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-600 select-none">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-teal-500/20 cursor-pointer accent-[#0d9488]"
                  />
                  <span>Ghi nhớ phiên đăng nhập</span>
                </label>
              </div>

              {error && (
                <Alert variant="destructive" className="mt-2">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              <div className="pt-2">
                <Button
                  type="submit"
                  variant="default"
                  className="w-full h-11 sm:h-12 rounded-xl text-sm font-semibold bg-[#5b6b78] hover:bg-[#4a5864] text-white shadow-sm flex items-center justify-center gap-2 transition-all cursor-pointer"
                  disabled={submitting || username.trim() === '' || password === ''}
                >
                  <span>{submitting ? 'Đang đăng nhập…' : 'Đăng nhập'}</span>
                  {!submitting && <ArrowRight className="w-4 h-4" />}
                </Button>
              </div>
            </form>
          </div>

          {/* Footer Metadata */}
          <footer className="pt-4 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-400 gap-2">
            <div className="flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-[#0d9488]" />
              <span>Bảo mật theo quy chuẩn khám chữa bệnh Bộ Y Tế</span>
            </div>
            <span>Phiên bản v1.0.0</span>
          </footer>
        </section>

        {/* Right Side: 50% width, Hospital Operations Hero matching reference design */}
        <section
          className="hidden lg:flex lg:w-1/2 flex-none bg-[#0b1c26] bg-gradient-to-br from-[#0c1f2b] via-[#0d222e] to-[#091720] text-white p-6 sm:p-8 lg:p-10 xl:p-12 flex-col justify-between relative overflow-hidden select-none"
          aria-label="Giới thiệu hệ thống"
        >
          {/* Subtle Ambient Decorative Glow */}
          <div className="absolute top-0 right-0 w-80 h-80 bg-[#0d9488]/10 rounded-full blur-3xl pointer-events-none" />


          {/* Top Bar / Status Indicator */}
          <div className="relative z-10 flex items-center justify-between">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-white/10 text-xs font-medium text-[#5eead4]">
              <Activity className="w-3.5 h-3.5 text-[#5eead4]" />
              <span>Hệ Thống Y Tế Trực Tuyến 24/7</span>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-slate-400">
              <Building2 className="w-3.5 h-3.5 text-slate-400" />
              <span>Hệ thống quản trị cơ sở y tế OS</span>
            </div>
          </div>

          {/* Center Showcase Content */}
          <div className="relative z-10 max-w-[540px] w-full mx-auto my-auto py-4">
            {/* Typography Header */}
            <div className="mb-6">
              <span className="text-sm font-bold text-[#2dd4bf] uppercase tracking-wider block mb-2">
                BỆNH VIỆN ĐA KHOA
              </span>
              <h2 className="text-3xl sm:text-[34px] xl:text-[38px] font-extrabold tracking-tight text-white leading-tight mb-3">
                METPH HEALTHCARE
              </h2>
              <p className="text-slate-200 text-[15px] sm:text-base leading-relaxed">
                Hệ thống quản lý luồng khám ngoại trú tinh gọn, chính xác và đồng bộ từ tiếp đón bệnh nhân đến khám bệnh và thanh toán viện phí.
              </p>
            </div>

            {/* 2 Symmetrical Operational Metric Cards */}
            <div className="grid grid-cols-2 gap-4 mb-6">
              <div className="bg-[#132735]/85 border border-white/10 rounded-2xl p-4 sm:p-5 shadow-sm backdrop-blur-sm flex flex-col justify-between">
                <div className="flex items-center justify-between gap-1 mb-3">
                  <span className="text-[13.5px] sm:text-sm text-slate-200 font-medium whitespace-nowrap">
                    Tổng lượt tiếp đón
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#0d9488]/30 border border-[#0d9488]/40 text-[#5eead4] shrink-0">
                    +2.14%
                  </span>
                </div>
                <div className="flex items-baseline gap-1.5 mb-2">
                  <span className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
                    300
                  </span>
                  <span className="text-[13.5px] text-slate-300 font-normal">bệnh nhân / ngày</span>
                </div>
                <div className="flex items-center gap-1.5 text-[12.5px] sm:text-xs text-slate-400">
                  <Users className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>Hoạt động 07:00 – 17:00</span>
                </div>
              </div>

              <div className="bg-[#132735]/85 border border-white/10 rounded-2xl p-4 sm:p-5 shadow-sm backdrop-blur-sm flex flex-col justify-between">
                <div className="flex items-center justify-between gap-1 mb-3">
                  <span className="text-[13.5px] sm:text-sm text-slate-200 font-medium whitespace-nowrap">
                    Phòng khám đang mở
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#0d9488]/30 border border-[#0d9488]/40 text-[#5eead4] shrink-0">
                    Đang khám
                  </span>
                </div>
                <div className="flex items-baseline gap-1.5 mb-2">
                  <span className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
                    7
                  </span>
                  <span className="text-[13.5px] text-slate-300 font-normal">buồng khám ngoại trú</span>
                </div>
                <div className="flex items-center gap-1.5 text-[12.5px] sm:text-xs text-slate-400">
                  <Stethoscope className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>Khoa Nội, Ngoại</span>
                </div>
              </div>
            </div>

            {/* 3 Core Workflow Capabilities */}
            <div className="space-y-3.5 pt-1">
              <div className="flex items-center gap-3 text-[15px] sm:text-base text-slate-100">
                <div className="w-5.5 h-5.5 rounded-full bg-[#0d9488]/25 text-[#2dd4bf] flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-4 h-4" />
                </div>
                <span>Tiếp đón bệnh nhân tự động & cấp số thứ tự điện tử</span>
              </div>
              <div className="flex items-center gap-3 text-[15px] sm:text-base text-slate-100">
                <div className="w-5.5 h-5.5 rounded-full bg-[#0d9488]/25 text-[#2dd4bf] flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-4 h-4" />
                </div>
                <span>Tra cứu thẻ BHYT & thanh toán viện phí tức thời</span>
              </div>
              <div className="flex items-center gap-3 text-[15px] sm:text-base text-slate-100">
                <div className="w-5.5 h-5.5 rounded-full bg-[#0d9488]/25 text-[#2dd4bf] flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-4 h-4" />
                </div>
                <span>Phân quyền chặt chẽ giữa Bác sĩ, Lễ tân & Thu ngân</span>
              </div>
            </div>
          </div>

          {/* Footer Metadata */}
          <footer className="relative z-10 flex items-center justify-between text-xs text-slate-400 pt-4 border-t border-white/10">
            <span>© 2026 Bệnh viện Đa khoa METPH</span>
            <div className="flex items-center gap-1.5">
              <Building2 className="w-3.5 h-3.5 text-slate-400" />
              <span>Hệ thống quản trị cơ sở y tế nội bộ</span>
            </div>
          </footer>
        </section>
      </div>
    </main>
  )
}
