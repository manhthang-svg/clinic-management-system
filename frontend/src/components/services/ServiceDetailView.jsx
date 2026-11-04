import { useState } from 'react';
import { FileText, Banknote, DoorOpen, FlaskConical, Pencil, Power, PowerOff } from 'lucide-react';
import { SERVICE_TYPE_MAP, SERVICE_STATUS_LABELS } from '../../constants/serviceData';
import { AccountPageHeader } from '../accounts/AccountFormParts';
import { formatPrice } from './serviceFormat';
import { ServiceInfoModal, ServicePriceModal } from './ServiceUpdateModals';

const SECONDARY_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer';
const PRIMARY_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer';
const DEACTIVATE_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-rose-600 bg-white border border-rose-200 rounded-xl hover:bg-rose-50 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';
const ACTIVATE_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-[#127e7a] bg-white border border-[#bfe0dc] rounded-xl hover:bg-teal-50 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';

const STATUS_TONES = {
  ACTIVE: 'bg-emerald-50 text-emerald-600',
  INACTIVE: 'bg-rose-50 text-rose-600',
};

function SectionCard({ icon: Icon, title, action, children }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="flex items-center gap-2.5 text-base! leading-6! font-bold! text-[#1b2533]">
          <Icon className="w-5 h-5 text-[#127e7a]" />
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function InfoList({ rows }) {
  return (
    <dl className="divide-y divide-slate-100 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="grid grid-cols-[150px_minmax(0,1fr)] gap-4 py-2.5">
          <dt className="text-slate-500">{label}</dt>
          <dd className="text-slate-800 break-words">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function PriceStat({ label, value, note }) {
  return (
    <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
      <div className="text-xs text-slate-500">{label}</div>
      {value === null || value === undefined ? (
        <div className="text-lg font-bold text-slate-400 mt-1">Không áp dụng</div>
      ) : (
        <div className="text-xl font-extrabold text-[#1b2533] mt-1">
          {formatPrice(value)} <span className="text-xs font-semibold text-slate-400">VNĐ</span>
        </div>
      )}
      {note && <div className="text-[11px] text-slate-400 mt-0.5">{note}</div>}
    </div>
  );
}

/** UC-21 View medical service detail, with UC-23 / UC-24 updates and UC-25 status change. */
export function ServiceDetailView({ service, onGoToList, onUpdateInfo, onUpdatePrices, onToggleStatus, statusBusy = false }) {
  // 'info' | 'price' | null
  const [editing, setEditing] = useState(null);
  const close = () => setEditing(null);
  const type = SERVICE_TYPE_MAP[service.type];
  const examination = service.type === 'EXAMINATION';
  const active = service.status === 'ACTIVE';
  const rooms = service.performingRooms ?? [];
  const labParameters = service.labParameters ?? [];

  const infoRows = [
    ['Mã dịch vụ', <span className="font-mono">{service.code}</span>],
    ['Tên dịch vụ', service.name],
    ['Loại dịch vụ', type?.label ?? service.type],
    ['Nhóm dịch vụ', service.serviceGroupName ?? (examination ? 'Không áp dụng' : '—')],
    ['Khoa thực hiện', service.departmentName ?? (examination ? 'Dùng chung các phòng khám' : '—')],
    ['Trạng thái', SERVICE_STATUS_LABELS[service.status] ?? service.status],
    ['Mô tả', service.description || '—'],
  ];

  return (
    <div className="space-y-6">
      <AccountPageHeader
        title="Chi tiết dịch vụ"
        description="Xem thông tin, giá và phòng thực hiện của dịch vụ"
        crumbs={[{ label: 'Quản lý dịch vụ và giá', onClick: onGoToList }, { label: 'Chi tiết dịch vụ' }]}
      />

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center gap-5">
        <div className="flex items-center gap-5 flex-1 min-w-0">
          <div className="w-20 h-20 rounded-2xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
            <FileText className="w-9 h-9" />
          </div>
          <div className="min-w-0">
            <div className="text-xs font-semibold text-slate-400 font-mono">{service.code}</div>
            <div className="text-2xl font-extrabold text-[#1b2533] break-words">{service.name}</div>
            <div className="flex flex-wrap items-center gap-2 mt-2">
              <span className={`inline-flex px-2.5 py-0.5 rounded-lg text-xs font-semibold border ${type?.color ?? ''}`}>
                {type?.label ?? service.type}
              </span>
              <span className={`inline-flex px-2.5 py-0.5 rounded-lg text-xs font-semibold ${STATUS_TONES[service.status] ?? ''}`}>
                {SERVICE_STATUS_LABELS[service.status] ?? service.status}
              </span>
            </div>
          </div>
        </div>
        {(onUpdateInfo || onUpdatePrices || onToggleStatus) && (
          <div className="flex flex-wrap gap-2.5 shrink-0">
            {onToggleStatus && (
              <button
                type="button"
                disabled={statusBusy}
                onClick={onToggleStatus}
                className={active ? DEACTIVATE_BUTTON_CLASS : ACTIVATE_BUTTON_CLASS}
              >
                {active ? <PowerOff className="w-4 h-4" /> : <Power className="w-4 h-4" />}
                {active ? 'Ngừng hoạt động' : 'Kích hoạt'}
              </button>
            )}
            {onUpdateInfo && (
              <button type="button" onClick={() => setEditing('info')} className={SECONDARY_BUTTON_CLASS}>
                <Pencil className="w-4 h-4" />
                Sửa thông tin
              </button>
            )}
            {onUpdatePrices && (
              <button type="button" onClick={() => setEditing('price')} className={PRIMARY_BUTTON_CLASS}>
                <Banknote className="w-4 h-4" />
                Cập nhật giá
              </button>
            )}
          </div>
        )}
      </section>

      {editing === 'info' && (
        <ServiceInfoModal
          service={service}
          onClose={close}
          onSubmit={async (form) => {
            await onUpdateInfo(form);
            close();
          }}
        />
      )}
      {editing === 'price' && (
        <ServicePriceModal
          service={service}
          onClose={close}
          onSubmit={async (form) => {
            await onUpdatePrices(form);
            close();
          }}
        />
      )}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-5 items-start">
        <SectionCard icon={FileText} title="Thông tin dịch vụ">
          <InfoList rows={infoRows} />
        </SectionCard>

        <div className="space-y-5">
          <SectionCard icon={Banknote} title="Giá dịch vụ">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <PriceStat label="Giá viện phí" value={service.clinicPrice} />
              <PriceStat
                label="Giá BHYT"
                value={service.insurancePrice}
                note={service.insurancePrice === null || service.insurancePrice === undefined
                  ? 'Không thuộc phạm vi BHYT, tính theo giá viện phí'
                  : undefined}
              />
            </div>
          </SectionCard>

          {service.type === 'LAB' && (
            <SectionCard icon={FlaskConical} title="Thông số xét nghiệm">
              {labParameters.length === 0 ? (
                <p className="text-sm text-slate-400">Chưa cấu hình thông số xét nghiệm.</p>
              ) : (
                <ol className="divide-y divide-slate-100">
                  {labParameters.map((parameter) => (
                    <li key={parameter.parameterId} className="flex items-center gap-3 py-2.5 text-sm">
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-xs font-bold text-[#127e7a]">
                        {parameter.displayOrder}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="font-semibold text-slate-800">{parameter.name}</div>
                        <div className="text-xs text-slate-400">
                          <span className="font-mono">{parameter.code}</span>
                          {parameter.unit ? ` · ${parameter.unit}` : ''}
                        </div>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </SectionCard>
          )}

          <SectionCard icon={DoorOpen} title="Phòng thực hiện">
            {rooms.length === 0 ? (
              <p className="text-sm text-slate-400">
                {examination ? 'Dịch vụ khám bệnh dùng chung cho các phòng khám.' : 'Chưa có phòng nào thực hiện dịch vụ này.'}
              </p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {rooms.map((room) => (
                  <li key={room.code} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-800">{room.name}</div>
                      <div className="text-xs text-slate-400 font-mono">{room.code}</div>
                    </div>
                    <div className="text-xs text-slate-500 text-right">{room.location || '—'}</div>
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

export default ServiceDetailView;
