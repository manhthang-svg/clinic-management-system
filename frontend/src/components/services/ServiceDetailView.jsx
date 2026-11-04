import { useState } from 'react';
import { FileText, Banknote, DoorOpen, FlaskConical, Pencil, Power, PowerOff } from 'lucide-react';
import { SERVICE_TYPE_MAP, SERVICE_STATUS_LABELS } from '../../constants/serviceData';
import { AccountPageHeader } from '../accounts/AccountFormParts';
import { formatPrice, formatReferenceRangeLine } from './serviceFormat';
import { LabValueTypeBadge } from './LabValueTypeBadge';
import { ServiceInfoModal, ServicePriceModal } from './ServiceUpdateModals';
import { FormAlert } from './ServiceFormParts';
import { MSG92 } from './serviceErrors';
import { LabReferenceRangeModal } from './LabReferenceRangeModal';

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
export function ServiceDetailView({
  service,
  onGoToList,
  onUpdateInfo,
  onUpdatePrices,
  onToggleStatus,
  referenceRangeApi,
  onReferenceRangesChanged,
  statusBusy = false,
  statusError = null,
}) {
  // 'info' | 'price' | null
  const [editing, setEditing] = useState(null);
  const [configuringParameter, setConfiguringParameter] = useState(null);
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

      {/* UC-25 MSG52 / MSG55 / MSG73 are shown in line, under the status button. */}
      {statusError && <FormAlert>{statusError}</FormAlert>}

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
      {configuringParameter && referenceRangeApi && (
        <LabReferenceRangeModal
          parameter={configuringParameter}
          api={referenceRangeApi}
          onChanged={onReferenceRangesChanged}
          onClose={() => setConfiguringParameter(null)}
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

          <SectionCard icon={DoorOpen} title="Phòng thực hiện">
            {rooms.length === 0 ? (
              <p className="text-sm text-slate-400">
                {/* UC-21 EF-03: a paraclinical service without an active room shows MSG92. */}
                {examination ? 'Dịch vụ khám bệnh dùng chung cho các phòng khám.' : MSG92}
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

      {service.type === 'LAB' && (
        <SectionCard
          icon={FlaskConical}
          title="Thông số xét nghiệm"
          action={labParameters.length > 0 && (
            <span className="text-xs font-semibold text-slate-500">{labParameters.length} chỉ số</span>
          )}
        >
          {labParameters.length === 0 ? (
            <p className="text-sm text-slate-400">{MSG92}</p>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-slate-100">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#f6f8f9] text-xs font-semibold text-[#1b2533]">
                    <th className="pl-4 pr-2 py-3 w-16 text-center">STT</th>
                    <th className="px-3 py-3">Mã chỉ số</th>
                    <th className="px-3 py-3">Tên chỉ số</th>
                    <th className="px-3 py-3">Kiểu kết quả</th>
                    <th className="px-3 py-3">Đơn vị</th>
                    <th className="px-3 py-3">Chỉ số tham chiếu</th>
                    {referenceRangeApi && <th className="px-3 py-3 text-center">Thao tác</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {labParameters.map((parameter) => (
                    <tr key={parameter.parameterId} className="hover:bg-slate-50/70">
                      <td className="pl-4 pr-2 py-3 text-center font-semibold text-[#127e7a]">{parameter.displayOrder}</td>
                      <td className="px-3 py-3 font-mono text-xs text-slate-500 whitespace-nowrap">{parameter.code}</td>
                      <td className="px-3 py-3 font-semibold text-slate-800">{parameter.name}</td>
                      <td className="px-3 py-3">
                        <LabValueTypeBadge valueType={parameter.valueType} />
                      </td>
                      <td className="px-3 py-3 text-slate-600">{parameter.unit || '—'}</td>
                      <td className="px-3 py-3 text-slate-700">
                        {(parameter.referenceRanges ?? []).length === 0 ? (
                          <span className="text-slate-400">—</span>
                        ) : (
                          <ul className="space-y-1">
                            {parameter.referenceRanges.map((range) => (
                              <li key={range.id}>{formatReferenceRangeLine(range)}</li>
                            ))}
                          </ul>
                        )}
                      </td>
                      {referenceRangeApi && (
                        <td className="px-3 py-3 text-center">
                          <button
                            type="button"
                            onClick={() => setConfiguringParameter({
                              id: parameter.parameterId,
                              code: parameter.code,
                              name: parameter.name,
                              unit: parameter.unit,
                            })}
                            className="inline-flex min-h-9 cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-teal-200 bg-white px-3 text-xs font-semibold text-[#127e7a] transition-colors hover:bg-teal-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#127e7a]/30"
                          >
                            <Pencil aria-hidden="true" className="h-3.5 w-3.5" />
                            Cấu hình
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>
      )}
    </div>
  );
}

export default ServiceDetailView;
