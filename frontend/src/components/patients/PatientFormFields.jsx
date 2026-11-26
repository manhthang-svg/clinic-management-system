import { useEffect, useState } from 'react';
import { MAX_NAME_LENGTH, countryOptions, ethnicityOptions, isForeigner, occupationOptions } from './patientForm';
import { clinicToday } from '../visits/visitForm';
import defaultAdministrativeUnitApi from '../../apis/administrativeUnitApi';

// form dung chung cho UC-37 tao ho so va UC-38 sua ho so
const inputClass =
    'mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 read-only:bg-slate-100';

const labelClass = 'block text-sm font-medium text-slate-700';

const sections = [
    {
        title: '1. Thông tin cá nhân',
        description: 'Thông tin định danh và liên hệ của bệnh nhân.',
    },
    {
        title: '2. Thông tin hành chính',
        description: 'Dân tộc, nghề nghiệp và địa chỉ cư trú.',
    },
    {
        title: '3. Thông tin người thân',
        description: 'Thông tin liên hệ người thân khi cần thiết.',
    },
];

// loi hien mau do duoi truong
export function FieldError({ name, error }) {
    return error ? (
        <p id={`${name}-error`} className="mt-1 text-xs font-medium text-red-600">{error}</p>
    ) : null;
}

export function Field({
    label,
    name,
    form,
    errors,
    onChange,
    onBlur,
    required = false,
    ...props
}) {
    const error = errors?.[name];
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>

            <input
                id={name}
                name={name}
                value={form[name] ?? ''}
                onChange={onChange}
                onBlur={onBlur}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? `${name}-error` : undefined}
                className={`${inputClass} ${error ? 'border-red-400' : ''}`}
                {...props}
            />
            <FieldError name={name} error={error} />
        </div>
    );
}

export function SelectField({
    label,
    name,
    form,
    errors,
    onChange,
    onBlur,
    options,
    required = false,
    ...props
}) {
    const error = errors?.[name];
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>

            <select
                id={name}
                name={name}
                value={form[name] ?? ''}
                onChange={onChange}
                onBlur={onBlur}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? `${name}-error` : undefined}
                className={`${inputClass} ${error ? 'border-red-400' : ''}`}
                {...props}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
            <FieldError name={name} error={error} />
        </div>
    );
}

// so sanh / loc ten khong phan biet hoa thuong va dau tieng Viet (go "ba dinh" ra "Phường Ba Đình")
const fold = (text) => (text ?? '').normalize('NFD').replace(/\p{M}/gu, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
const sameName = (a, b) => fold(a) === fold(b);

/**
 * O nhap co bang goi y trang xo xuong (giong cac o chon khac) nhung go duoc de loc.
 * Chon bang chuot hoac phim mui ten / Enter; Esc dong bang.
 */
export function SuggestField({
    label, name, form, errors, required = false, placeholder, options, onType, onPick, onBlur, emptyText,
}) {
    const [open, setOpen] = useState(false);
    const [active, setActive] = useState(-1);
    const value = form[name] ?? '';
    const error = errors?.[name];
    const listId = `${name}-options`;
    const query = fold(value);
    // dang hien dung ten da chon thi hien ca danh sach de chon lai
    const shown = !query || options.some((item) => fold(item.name) === query)
        ? options
        : options.filter((item) => fold(item.name).includes(query));

    const pick = (item) => {
        onPick(item);
        setOpen(false);
        setActive(-1);
    };

    const handleKeyDown = (event) => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            setOpen(true);
            if (!shown.length) return;
            const step = event.key === 'ArrowDown' ? 1 : -1;
            setActive((current) => (current + step + shown.length) % shown.length);
        } else if (event.key === 'Enter' && open && shown[active]) {
            event.preventDefault();
            pick(shown[active]);
        } else if (event.key === 'Escape') {
            setOpen(false);
        }
    };

    return (
        <div className="relative">
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>
            <input
                id={name}
                name={name}
                value={value}
                role="combobox"
                aria-expanded={open}
                aria-controls={listId}
                aria-autocomplete="list"
                aria-activedescendant={open && shown[active] ? `${listId}-${active}` : undefined}
                aria-invalid={error ? 'true' : undefined}
                aria-describedby={error ? `${name}-error` : undefined}
                autoComplete="off"
                placeholder={placeholder}
                className={`${inputClass} ${error ? 'border-red-400' : ''}`}
                onFocus={() => setOpen(true)}
                onClick={() => setOpen(true)}
                onChange={(event) => {
                    onType(event.target.value);
                    setOpen(true);
                    setActive(-1);
                }}
                onKeyDown={handleKeyDown}
                onBlur={(event) => {
                    setOpen(false);
                    onBlur?.(event);
                }}
            />
            {open && (
                <ul
                    id={listId}
                    role="listbox"
                    aria-label={`Danh sách ${label}`}
                    className="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white p-1 shadow-lg"
                >
                    {shown.length ? shown.map((item, index) => (
                        <li
                            key={item.code}
                            id={`${listId}-${index}`}
                            role="option"
                            aria-selected={fold(item.name) === query}
                            // giu focus o o nhap de bang khong dong truoc khi chon
                            onMouseDown={(event) => event.preventDefault()}
                            onClick={() => pick(item)}
                            className={`cursor-pointer rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-teal-50 ${
                                index === active ? 'bg-teal-50' : ''} ${fold(item.name) === query ? 'font-semibold text-teal-700' : ''}`}
                        >
                            {item.name}
                        </li>
                    )) : (
                        <li className="px-3 py-2 text-sm text-slate-400">{emptyText}</li>
                    )}
                </ul>
            )}
            <FieldError name={name} error={error} />
        </div>
    );
}

/**
 * UC-37, UC-38: Tỉnh/thành phố và Xã/phường gợi ý từ danh mục đơn vị hành chính (BR-46, BR-89).
 * Chọn một tên trong danh sách thì mã tự điền; đổi tỉnh thì xóa xã đã chọn.
 * Không tải được danh mục thì vẫn cho nhập tay (mã để trống).
 */
export function AdministrativeUnitFields({ form, fieldErrors, handleChange, handleBlur, required, unitApi }) {
    const [provinces, setProvinces] = useState([]);
    // xa/phuong cua tinh da tai; doi tinh thi danh sach cu khong con dung
    const [loadedWards, setLoadedWards] = useState({ provinceCode: '', items: [] });
    const [loadFailed, setLoadFailed] = useState(false);
    const provinceCode = form.tinhthanhMa ?? '';
    const wards = provinceCode && loadedWards.provinceCode === provinceCode ? loadedWards.items : [];

    useEffect(() => {
        const controller = new AbortController();
        unitApi.provinces(controller.signal)
            .then(setProvinces)
            .catch(() => { if (!controller.signal.aborted) setLoadFailed(true); });
        return () => controller.abort();
    }, [unitApi]);

    useEffect(() => {
        if (!provinceCode) return undefined;
        const controller = new AbortController();
        unitApi.wards(provinceCode, controller.signal)
            .then((items) => setLoadedWards({ provinceCode, items }))
            .catch(() => { if (!controller.signal.aborted) setLoadFailed(true); });
        return () => controller.abort();
    }, [unitApi, provinceCode]);

    const emit = (name, value) => handleChange({ target: { name, value } });

    const setProvince = (value, match) => {
        const code = match?.code ?? '';
        emit('tinhthanh', match ? match.name : value);
        if (code !== provinceCode) {
            emit('tinhthanhMa', code);
            emit('xaphuong', '');
            emit('xaphuongMa', '');
        }
    };

    const setWard = (value, match) => {
        emit('xaphuong', match ? match.name : value);
        emit('xaphuongMa', match?.code ?? '');
    };

    return (
        <>
            <SuggestField
                label="Tỉnh/thành phố"
                name="tinhthanh"
                form={form}
                errors={fieldErrors}
                required={required}
                placeholder="Gõ hoặc chọn tỉnh/thành phố"
                options={provinces}
                onType={(value) => setProvince(value, provinces.find((item) => sameName(item.name, value)))}
                onPick={(item) => setProvince(item.name, item)}
                onBlur={handleBlur}
                emptyText={loadFailed ? 'Không tải được danh mục' : 'Không có tỉnh/thành phố phù hợp'}
            />

            <SuggestField
                label="Xã/phường"
                name="xaphuong"
                form={form}
                errors={fieldErrors}
                required={required}
                placeholder={provinceCode ? 'Gõ hoặc chọn xã/phường' : 'Chọn tỉnh/thành phố trước'}
                options={wards}
                onType={(value) => setWard(value, wards.find((item) => sameName(item.name, value)))}
                onPick={(item) => setWard(item.name, item)}
                onBlur={handleBlur}
                emptyText={provinceCode ? 'Không có xã/phường phù hợp' : 'Chọn tỉnh/thành phố trước'}
            />

            {loadFailed && (
                <p className="text-xs text-amber-700 md:col-span-2 xl:col-span-3">
                    Không tải được danh mục đơn vị hành chính. Vẫn có thể nhập tay tỉnh/thành phố và xã/phường.
                </p>
            )}
        </>
    );
}

export function Dialog({ title, children }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div role="dialog" aria-modal="true" aria-label={title} className="w-full max-w-lg space-y-4 rounded-2xl bg-white p-6 shadow-xl">
                <h3 className="text-lg font-bold text-slate-800">{title}</h3>
                {children}
            </div>
        </div>
    );
}

// 3 phan cua form; dau bat buoc SDT / nguoi than theo tuoi
export function PatientFormSections({
    form, fieldErrors, handleChange, handleBlur, adult, minor, unitApi = defaultAdministrativeUnitApi,
}) {
    return (
        <>
                {/* 1. Thong tin ca nhan */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[0].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[0].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <Field
                            label="Họ và tên"
                            name="hoten"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required
                            maxLength={MAX_NAME_LENGTH}
                            placeholder="Nhập họ và tên"
                        />

                        <Field
                            label="Ngày sinh"
                            name="ngaysinh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            type="date"
                            required
                            max={clinicToday()}
                        />

                        <SelectField
                            label="Giới tính"
                            name="gioitinh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required
                            options={[
                                { value: '1', label: 'Nam' },
                                { value: '2', label: 'Nữ' },
                                { value: '3', label: 'Chưa xác định' },
                            ]}
                        />

                        <Field
                            label="CCCD"
                            name="cccdSo"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            inputMode="numeric"
                            maxLength={12}
                            placeholder="12 chữ số"
                        />

                        <Field
                            label="Số hộ chiếu"
                            name="hochieuSo"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            maxLength={15}
                            placeholder="Nếu có"
                        />

                        <Field
                            label="Số điện thoại"
                            name="sodienthoai"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            type="tel"
                            required={adult}
                            maxLength={10}
                            placeholder="10 chữ số, bắt buộc từ 18 tuổi"
                        />

                        <Field
                            label="Email"
                            name="email"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            type="email"
                            autoComplete="off"
                            placeholder="Để gửi email nhắc lịch tái khám"
                        />

                        <SelectField
                            label="Dân tộc"
                            name="dantoc"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required
                            options={ethnicityOptions(form.dantoc)}
                        />

                        <Field
                            label="Mã dân tộc"
                            name="dantocMa"
                            form={form}
                            errors={fieldErrors}
                            readOnly
                            disabled
                            title="Tự điền theo dân tộc đã chọn"
                            placeholder="Tự điền theo dân tộc"
                        />

                        <SelectField
                            label="Nhóm máu"
                            name="nhommau"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            options={[
                                { value: '', label: 'Chưa xác định' },
                                { value: 'A', label: 'A' },
                                { value: 'B', label: 'B' },
                                { value: 'AB', label: 'AB' },
                                { value: 'O', label: 'O' },
                            ]}
                        />

                        <Field
                            label="Yếu tố Rh"
                            name="yeutorh"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            maxLength={5}
                            placeholder="Ví dụ: Rh+"
                        />
                    </div>
                </section>

                {/* 2. Thong tin hanh chinh */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[1].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[1].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <SelectField
                            label="Nghề nghiệp"
                            name="nghenghiep"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required
                            options={occupationOptions(form.nghenghiep)}
                        />

                        <Field
                            label="Nơi làm việc"
                            name="noilamviec"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            placeholder="Tên cơ quan hoặc nơi làm việc"
                        />

                        <SelectField
                            label="Quốc tịch"
                            name="ngoaikieu"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required={isForeigner(form)}
                            // ho so cu co nuoc nhung khong phai ngoai kieu van mo de bo chon
                            disabled={!isForeigner(form) && !form.ngoaikieu}
                            title={isForeigner(form) ? undefined : 'Chỉ chọn khi nghề nghiệp là Ngoại kiều'}
                            options={countryOptions(form.ngoaikieu)}
                        />

                        <AdministrativeUnitFields
                            form={form}
                            fieldErrors={fieldErrors}
                            handleChange={handleChange}
                            handleBlur={handleBlur}
                            required={!isForeigner(form)}
                            unitApi={unitApi}
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ"
                                name="diachi"
                                form={form}
                                errors={fieldErrors}
                                onChange={handleChange}
                                onBlur={handleBlur}
                                required
                                maxLength={1024}
                                placeholder="Số nhà, đường, thôn/xóm..."
                            />
                        </div>
                    </div>
                </section>

                {/* 3. Thong tin nguoi than */}
                <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm sm:p-6">
                    <div className="mb-6 border-b border-slate-100 pb-4">
                        <h2 className="font-bold text-slate-800">
                            {sections[2].title}
                        </h2>
                        <p className="mt-1 text-xs text-slate-400">
                            {sections[2].description}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        <Field
                            label="Họ tên người thân"
                            name="hotennguoithan"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required={minor}
                            maxLength={255}
                            placeholder="Họ tên người liên hệ"
                        />

                        <SelectField
                            label="Quan hệ"
                            name="nguoithanQuanhe"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            required={minor}
                            options={[
                                { value: '', label: 'Chọn quan hệ' },
                                { value: 'Bố', label: 'Bố' },
                                { value: 'Mẹ', label: 'Mẹ' },
                                { value: 'Vợ', label: 'Vợ' },
                                { value: 'Chồng', label: 'Chồng' },
                                { value: 'Con', label: 'Con' },
                                { value: 'Anh/chị/em', label: 'Anh/chị/em' },
                                { value: 'Khác', label: 'Khác' },
                            ]}
                        />

                        <Field
                            label="Số điện thoại người thân"
                            name="sodienthoainguoithan"
                            form={form}
                            errors={fieldErrors}
                            onChange={handleChange}
                            onBlur={handleBlur}
                            type="tel"
                            required={minor}
                            maxLength={10}
                            placeholder={minor ? '10 chữ số, bắt buộc dưới 18 tuổi' : 'Số điện thoại liên hệ'}
                        />

                        <div className="md:col-span-2 xl:col-span-3">
                            <Field
                                label="Địa chỉ người thân"
                                name="diachinguoithan"
                                form={form}
                                errors={fieldErrors}
                                onChange={handleChange}
                                onBlur={handleBlur}
                                maxLength={1024}
                                placeholder="Địa chỉ người thân"
                            />
                        </div>
                    </div>
                </section>
        </>
    );
}
