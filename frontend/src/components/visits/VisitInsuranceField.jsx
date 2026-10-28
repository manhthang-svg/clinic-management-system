import { ShieldCheck, ShieldAlert, ShieldQuestion, RefreshCw } from 'lucide-react';
import { INPUT_CLASS, Field } from '../shared/FormFields';
import { MSG20 } from './visitErrors';
import { CARD_NUMBER_LENGTH, coverageRateOf, isCardNumberFormatValid } from './visitForm';
import { formatDate } from './visitFormat';

const SMALL_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';

function Panel({ tone, icon: Icon, title, children }) {
  const tones = {
    success: 'bg-emerald-50 border-emerald-200 text-emerald-800',
    warning: 'bg-amber-50 border-amber-200 text-amber-800',
  };
  return (
    <div role={tone === 'warning' ? 'alert' : 'status'} className={`p-3.5 rounded-xl border space-y-2 ${tones[tone]}`}>
      <p className="flex items-start gap-2 text-sm font-semibold">
        <Icon className="w-4 h-4 shrink-0 mt-0.5" />
        {title}
      </p>
      {children}
    </div>
  );
}

/** UC-40 step 6 with UC-74: card number, Verify, and the verification outcome. */
export function VisitInsuranceField({
  value,
  onChange,
  insurance,
  error,
  onVerify,
  onRecordUnverified,
  onSwitchToSelfPaid,
}) {
  const current = insurance.cardNumber === value ? insurance.status : null;
  const verifying = current === 'VERIFYING';
  const card = insurance.card;

  return (
    <div className="space-y-3">
      <Field
        id="visit-card-number"
        label="Số thẻ BHYT"
        required
        error={error}
        hint={`${CARD_NUMBER_LENGTH} ký tự, ký tự thứ 3 là mã quyền lợi từ 1 đến 5`}
      >
        <div className="flex gap-2.5">
          <input
            id="visit-card-number"
            type="text"
            value={value}
            onChange={(e) => onChange(e.target.value.toUpperCase().replace(/\s/g, '').slice(0, CARD_NUMBER_LENGTH))}
            placeholder="VD: CC1790000000001"
            autoComplete="off"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'visit-card-number-error' : undefined}
            className={`${INPUT_CLASS} font-mono uppercase`}
          />
          <button
            type="button"
            onClick={onVerify}
            disabled={verifying || !isCardNumberFormatValid(value)}
            className="shrink-0 inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-[#127e7a] bg-[#e6f5f3] border border-[#bfe0dc] rounded-xl hover:bg-[#d8efec] cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <ShieldCheck className="w-4 h-4" />
            {verifying ? 'Đang xác minh…' : 'Xác minh'}
          </button>
        </div>
      </Field>

      {current === 'VALID' && (
        // MSG16
        <Panel
          tone="success"
          icon={ShieldCheck}
          title={`Thẻ BHYT có giá trị đến ${formatDate(card?.validTo)}. Mức hưởng: ${coverageRateOf(card) ?? '—'}%.`}
        >
          {card?.primaryCareFacility && (
            <p className="text-xs">Nơi đăng ký KCB ban đầu: {card.primaryCareFacility}</p>
          )}
        </Panel>
      )}

      {current === 'MISMATCH' && (
        // MSG18
        <Panel
          tone="warning"
          icon={ShieldAlert}
          title={`Thông tin thẻ BHYT (${card?.fullName ?? '—'}, ${formatDate(card?.dateOfBirth)}) không khớp với hồ sơ bệnh nhân. Vui lòng kiểm tra lại.`}
        >
          <p className="text-xs">Sửa lại số thẻ rồi xác minh lại, hoặc chuyển sang lượt khám dịch vụ.</p>
          <button type="button" onClick={onSwitchToSelfPaid} className={`${SMALL_BUTTON_CLASS} bg-white border border-amber-300 text-amber-800 hover:bg-amber-100`}>
            Chuyển sang Dịch vụ
          </button>
        </Panel>
      )}

      {current === 'UNAVAILABLE' && (
        <Panel tone="warning" icon={ShieldQuestion} title={MSG20}>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={onVerify} className={`${SMALL_BUTTON_CLASS} bg-white border border-amber-300 text-amber-800 hover:bg-amber-100`}>
              <RefreshCw className="w-3.5 h-3.5" />
              Xác minh lại
            </button>
            <button type="button" onClick={onRecordUnverified} className={`${SMALL_BUTTON_CLASS} bg-amber-600 text-white hover:bg-amber-700`}>
              Ghi nhận thẻ chưa xác minh
            </button>
          </div>
        </Panel>
      )}

      {current === 'UNVERIFIED' && (
        <Panel tone="warning" icon={ShieldQuestion} title="Thẻ BHYT chưa xác minh">
          <p className="text-xs">Lượt khám vẫn là BHYT; thẻ sẽ được xác minh lại trước khi bệnh nhân thanh toán tại quầy thu ngân.</p>
          <button type="button" onClick={onVerify} className={`${SMALL_BUTTON_CLASS} bg-white border border-amber-300 text-amber-800 hover:bg-amber-100`}>
            <RefreshCw className="w-3.5 h-3.5" />
            Xác minh lại
          </button>
        </Panel>
      )}
    </div>
  );
}
