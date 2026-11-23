import { ArrowLeft, Save } from 'lucide-react';
import { useCreatePatient } from '../../hooks/useCreatePatient';
import { ADULT_AGE, MSG, formAge, msg57 } from '../../components/patients/patientForm';
import { Dialog, PatientFormSections } from '../../components/patients/PatientFormFields';
import { clinicToday } from '../../components/visits/visitForm';

// UC-37: trang tao ho so benh nhan
export default function ReceptionistCreatePatientPage({ patientApi }) {
    // hook quan ly form, kiem tra, luu
    const {
        form,
        fieldErrors,
        error,
        conflict,
        duplicates,
        confirmCancel,
        submitting,
        handleChange,
        handleBlur,
        handleSubmit,
        goBack,
        openProfile,
        createAnyway,
        closeDuplicates,
        keepEditing,
        discard,
    } = useCreatePatient(patientApi);

    // truong bat buoc theo tuoi: tu 18 tuoi can SDT, duoi 18 can nguoi than
    const age = formAge(form, clinicToday());
    const adult = age !== null && age >= ADULT_AGE;
    const minor = age !== null && age < ADULT_AGE;

    return (
        <div className="mx-auto w-full max-w-[1500px] space-y-6">
            <header className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <div className="mb-2 flex items-center gap-2 text-xs text-slate-400">
                        <span>Bệnh nhân</span>
                        <span>/</span>
                        <span className="text-teal-700">
                            Tạo hồ sơ bệnh nhân mới
                        </span>
                    </div>

                    <h1 className="text-2xl font-bold tracking-tight text-slate-800">
                        Tạo hồ sơ bệnh nhân mới
                    </h1>

                    <p className="mt-1 text-sm text-slate-500">
                        Nhập thông tin hành chính theo hồ sơ bệnh nhân.
                    </p>
                </div>

                <button
                    type="button"
                    onClick={goBack}
                    disabled={submitting}
                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
                >
                    <ArrowLeft size={16} />
                    Quay lại
                </button>
            </header>

            <form onSubmit={handleSubmit} noValidate className="space-y-5">
                <PatientFormSections
                    form={form}
                    fieldErrors={fieldErrors}
                    handleChange={handleChange}
                    handleBlur={handleBlur}
                    adult={adult}
                    minor={minor}
                />

                {conflict && (
                    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        <span>{fieldErrors[conflict.field]}</span>
                        <button
                            type="button"
                            onClick={() => openProfile(conflict.patientId)}
                            className="rounded-lg border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100"
                        >
                            Mở hồ sơ
                        </button>
                    </div>
                )}

                {error && !conflict && (
                    <div
                        role="alert"
                        className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                    >
                        {error}
                    </div>
                )}

                <div className="flex flex-wrap justify-end gap-3 pb-6">
                    <button
                        type="button"
                        onClick={goBack}
                        disabled={submitting}
                        className="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
                    >
                        Hủy
                    </button>

                    <button
                        type="submit"
                        disabled={submitting}
                        className="inline-flex items-center gap-2 rounded-xl bg-[#0F9D9A] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        <Save size={16} />
                        {submitting ? 'Đang lưu…' : 'Lưu hồ sơ'}
                    </button>
                </div>
            </form>

            {duplicates && (
                <Dialog title="Có thể trùng hồ sơ">
                    <p className="text-sm text-slate-600">{msg57(duplicates.length)}</p>
                    <ul className="max-h-60 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-100">
                        {duplicates.map((patient) => (
                            <li key={patient.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                                <span>
                                    <span className="font-mono text-teal-700">{patient.mabenhnhan}</span>
                                    {' · '}{patient.hoten}
                                    {patient.sodienthoai ? ` · ${patient.sodienthoai}` : ''}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => openProfile(patient.id)}
                                    className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                >
                                    Chọn hồ sơ này
                                </button>
                            </li>
                        ))}
                    </ul>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={closeDuplicates} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">
                            Quay lại chỉnh sửa
                        </button>
                        <button
                            type="button"
                            onClick={() => void createAnyway()}
                            disabled={submitting}
                            className="rounded-xl bg-[#0F9D9A] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
                        >
                            Vẫn tạo hồ sơ mới
                        </button>
                    </div>
                </Dialog>
            )}

            {confirmCancel && (
                <Dialog title="Hủy tạo hồ sơ">
                    <p className="text-sm text-slate-600">{MSG.DISCARD}</p>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={keepEditing} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">
                            Tiếp tục chỉnh sửa
                        </button>
                        <button type="button" onClick={discard} className="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white">
                            Loại bỏ
                        </button>
                    </div>
                </Dialog>
            )}
        </div>
    );
}
