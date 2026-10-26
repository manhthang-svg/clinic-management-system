
const inputClass =
    'mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100';

const labelClass = 'block text-sm font-medium text-slate-700';

export function Field({
    label,
    name,
    form,
    onChange,
    required = false,
    ...props
}) {
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>
            <input
                id={name}
                name={name}
                value={form[name]}
                onChange={onChange}
                required={required}
                className={inputClass}
                {...props}
            />
        </div>
    );
}

export function SelectField({
    label,
    name,
    form,
    onChange,
    options,
    required = false,
}) {
    return (
        <div>
            <label htmlFor={name} className={labelClass}>
                {label}
                {required && <span className="ml-1 text-red-500">*</span>}
            </label>
            <select
                id={name}
                name={name}
                value={form[name]}
                onChange={onChange}
                required={required}
                className={inputClass}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </div>
    );
}
