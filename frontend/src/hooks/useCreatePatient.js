import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router';
import patientApi from '../apis/patientApi';

const initialForm = {
    hoten: '',
    ngaysinh: '',
    gioitinh: '1',
    cccdSo: '',
    hochieuSo: '',
    nghenghiep: '',
    nghenghiepMa: '',
    dantoc: 'Kinh',
    dantocMa: '01',
    ngoaikieu: '',
    ngoaikieuMa: '',
    diachi: '',
    xaphuong: '',
    xaphuongMa: '',
    tinhthanh: '',
    tinhthanhMa: '',
    noilamviec: '',
    sodienthoai: '',
    yeutorh: '',
    nhommau: '',
    hotennguoithan: '',
    nguoithanQuanhe: '',
    diachinguoithan: '',
    sodienthoainguoithan: '',
};

function validatePatient(form) {
    if (!form.hoten.trim()) {
        return 'Vui lòng nhập họ và tên bệnh nhân.';
    }

    if (!form.ngaysinh) {
        return 'Vui lòng chọn ngày sinh.';
    }

    const today = new Date();
    const todayString = [
        today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, '0'),
        String(today.getDate()).padStart(2, '0'),
    ].join('-');

    if (form.ngaysinh > todayString) {
        return 'Ngày sinh không được ở tương lai.';
    }

    if (!['1', '2', '3'].includes(form.gioitinh)) {
        return 'Giới tính không hợp lệ.';
    }

    if (!form.dantoc.trim() || !form.dantocMa.trim()) {
        return 'Vui lòng nhập dân tộc và mã dân tộc.';
    }

    if (!form.diachi.trim()) {
        return 'Vui lòng nhập địa chỉ bệnh nhân.';
    }

    const cccd = form.cccdSo.trim();
    if (cccd && !/^\d{12}$/.test(cccd)) {
        return 'Nếu nhập CCCD, vui lòng nhập đủ 12 chữ số.';
    }

    const phone = form.sodienthoai.trim();
    if (phone && !/^\d{9,15}$/.test(phone)) {
        return 'Số điện thoại phải gồm 9–15 chữ số.';
    }

    const relativePhone = form.sodienthoainguoithan.trim();
    if (relativePhone && !/^\d{9,15}$/.test(relativePhone)) {
        return 'Số điện thoại người thân phải gồm 9–15 chữ số.';
    }

    return '';
}

function buildPatientPayload(form) {
    const nullableFields = [
        'cccdSo',
        'hochieuSo',
        'nghenghiep',
        'nghenghiepMa',
        'ngoaikieu',
        'ngoaikieuMa',
        'xaphuong',
        'xaphuongMa',
        'tinhthanh',
        'tinhthanhMa',
        'noilamviec',
        'sodienthoai',
        'yeutorh',
        'nhommau',
        'hotennguoithan',
        'nguoithanQuanhe',
        'diachinguoithan',
        'sodienthoainguoithan',
    ];

    const payload = {
        ...form,
        hoten: form.hoten.trim(),
        gioitinh: Number(form.gioitinh),
        dantoc: form.dantoc.trim(),
        dantocMa: form.dantocMa.trim(),
        diachi: form.diachi.trim(),
    };

    nullableFields.forEach((field) => {
        payload[field] = form[field].trim() || null;
    });

    return payload;
}

export function useCreatePatient() {
    const navigate = useNavigate();

    const [form, setForm] = useState(initialForm);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleChange = useCallback((event) => {
        const { name, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [name]: value,
        }));

        setError('');
    }, []);

    const handleSubmit = useCallback(async (event) => {
        event.preventDefault();

        if (submitting) return;

        setError('');

        const validationError = validatePatient(form);
        if (validationError) {
            setError(validationError);
            return;
        }

        setSubmitting(true);

        try {
            const res = await patientApi.create(buildPatientPayload(form));
            const created = res?.data || res;

            navigate('/receptionist', {
                replace: true,
                state: {
                    notice: `Tạo hồ sơ bệnh nhân "${form.hoten.trim()}" thành công!`,
                    openVisitFor: created,
                },
            });
        } catch (err) {
            setError(
                err?.message ||
                'Không thể tạo hồ sơ bệnh nhân. Vui lòng thử lại.',
            );
        } finally {
            setSubmitting(false);
        }
    }, [form, navigate, submitting]);

    const goBack = useCallback(() => {
        if (!submitting) {
            navigate('/receptionist');
        }
    }, [navigate, submitting]);

    return {
        form,
        error,
        submitting,
        handleChange,
        handleSubmit,
        goBack,
    };
}
