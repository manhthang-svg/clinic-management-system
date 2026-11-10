import { User, AtSign, Calendar, Phone, Mail, MapPin, GraduationCap, Users, BadgeCheck } from 'lucide-react';
import { GENDER_LABELS } from '../../constants/accountData';
import { FormField, INPUT_CLASS } from './AccountFormParts';

const WITH_ICON = `${INPUT_CLASS} pl-10`;

/**
 * Inputs for the account attributes (staff-account.md). `editable` lists the fields the screen may change;
 * the others are shown read-only. `showUsername` is false where the username is shown elsewhere.
 */
export function AccountPersonalFields({ form, errors, onChange, editable, showUsername = true, gridClass = 'md:grid-cols-2' }) {
  const canEdit = (field) => editable.includes(field);
  const requiresProfessionalInfo = form.role === 'DOCTOR' || form.role === 'TECHNICIAN';
  const props = (field) => ({
    id: `account-${field}`,
    value: form[field],
    disabled: !canEdit(field),
    onChange: (e) => onChange(field, e.target.value),
    'aria-invalid': errors[field] ? true : undefined,
    'aria-describedby': errors[field] ? `account-${field}-error` : undefined,
  });

  return (
    <div className={`grid grid-cols-1 ${gridClass} gap-x-5 gap-y-4`}>
      <FormField id="account-fullName" label="Họ và tên" required={canEdit('fullName')} error={errors.fullName} icon={User}>
        <input type="text" placeholder="vd: Nguyễn Văn A" maxLength={255} className={WITH_ICON} {...props('fullName')} />
      </FormField>

      {showUsername && (
        <FormField
          id="account-username"
          label="Tên đăng nhập"
          required={canEdit('username')}
          error={errors.username}
          hint={canEdit('username') ? '4–30 ký tự: a–z, 0–9, dấu chấm và gạch dưới. Không đổi được sau khi tạo.' : null}
          icon={AtSign}
        >
          <input type="text" placeholder="vd: bs.nguyenvana" maxLength={30} autoComplete="off" className={WITH_ICON} {...props('username')} />
        </FormField>
      )}

      <FormField id="account-gender" label="Giới tính" error={errors.gender} icon={Users}>
        <select className={`${WITH_ICON} cursor-pointer`} {...props('gender')}>
          <option value="">— Chưa chọn —</option>
          {Object.entries(GENDER_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </FormField>

      <FormField id="account-dateOfBirth" label="Ngày sinh" error={errors.dateOfBirth} icon={Calendar}>
        <input type="date" max={new Date().toISOString().slice(0, 10)} className={WITH_ICON} {...props('dateOfBirth')} />
      </FormField>

      <FormField id="account-phone" label="Số điện thoại" required={canEdit('phone')} error={errors.phone} icon={Phone}>
        <input type="tel" inputMode="numeric" placeholder="09xxxxxxxx" maxLength={10} className={WITH_ICON} {...props('phone')} />
      </FormField>

      <FormField id="account-email" label="Email" error={errors.email} icon={Mail}>
        <input type="email" placeholder="ten@clinic.example" maxLength={100} className={WITH_ICON} {...props('email')} />
      </FormField>

      <FormField
        id="account-title"
        label="Học hàm / học vị"
        required={canEdit('title') && requiresProfessionalInfo}
        error={errors.title}
        hint={canEdit('title')
          ? requiresProfessionalInfo
            ? 'Bắt buộc đối với Bác sĩ và Kỹ thuật viên.'
            : 'Không bắt buộc, vd: BSCKI, ThS.BS'
          : null}
        icon={GraduationCap}
      >
        <input
          type="text"
          placeholder="vd: ThS.BS"
          maxLength={50}
          required={canEdit('title') && requiresProfessionalInfo}
          className={WITH_ICON}
          {...props('title')}
        />
      </FormField>

      <FormField
        id="account-practiceLicenseNo"
        label="Số giấy phép hành nghề"
        required={canEdit('practiceLicenseNo') && requiresProfessionalInfo}
        error={errors.practiceLicenseNo}
        hint={canEdit('practiceLicenseNo') ? 'Bắt buộc đối với Bác sĩ và Kỹ thuật viên.' : null}
        icon={BadgeCheck}
      >
        <input
          type="text"
          placeholder="vd: 000123/HCM-CCHN"
          maxLength={255}
          required={canEdit('practiceLicenseNo') && requiresProfessionalInfo}
          className={WITH_ICON}
          {...props('practiceLicenseNo')}
        />
      </FormField>

      <FormField
        id="account-address"
        label="Địa chỉ"
        error={errors.address}
        hint={canEdit('address') ? `${form.address.length}/255` : null}
        icon={MapPin}
        className="md:col-span-full"
      >
        <textarea rows={2} maxLength={255} placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố" className={`${WITH_ICON} resize-none`} {...props('address')} />
      </FormField>
    </div>
  );
}

export default AccountPersonalFields;
