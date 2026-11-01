import { useEffect, useState } from 'react';
import { apiClient } from '../services/session';
import { createServiceApi } from '../apis/serviceApi';

const serviceApi = createServiceApi(apiClient);

// Backend chỉ chấp nhận size trong [10, 20, 50] (MedicalServiceServiceImpl#validatePage).
const MAX_PAGE_SIZE = 50;

const EMPTY = { services: [], loading: false, error: null };

/**
 * Lấy danh mục dịch vụ y tế đang hoạt động từ Backend:
 * - Đối với phòng khám lâm sàng (EXAMINATION): lấy dịch vụ khám (type = 'EXAMINATION').
 * - Đối với phòng cận lâm sàng (PARACLINICAL): lọc theo serviceGroupId (Nhóm dịch vụ kỹ thuật)
 *   hoặc departmentId của khoa cận lâm sàng trực thuộc.
 * - Hỗ trợ truyền dạng tham số object { departmentId, serviceGroupId, roomType }
 *   hoặc truyền trực tiếp departmentId (string).
 */
export function useDepartmentServices(options) {
  const isObject = options && typeof options === 'object';
  const departmentId = isObject ? options.departmentId : options;
  const serviceGroupId = isObject ? options.serviceGroupId : null;
  const roomType = isObject ? options.roomType : null;

  const shouldFetch = Boolean(
    (roomType === 'EXAMINATION') ||
    serviceGroupId ||
    departmentId
  ) && roomType !== 'ADMINISTRATIVE';

  const queryKey = shouldFetch
    ? `${roomType || ''}:${serviceGroupId || ''}:${departmentId || ''}`
    : null;

  const [result, setResult] = useState({ queryKey: null, services: [], error: null });

  useEffect(() => {
    if (!shouldFetch || !queryKey) return undefined;
    const controller = new AbortController();

    const params = {
      status: 'ACTIVE',
      size: MAX_PAGE_SIZE,
    };

    if (roomType === 'EXAMINATION') {
      params.type = 'EXAMINATION';
    } else {
      if (serviceGroupId) {
        params.group = serviceGroupId;
      }
      if (departmentId) {
        params.department = departmentId;
      }
    }

    serviceApi
      .list(params, controller.signal)
      .then((page) => {
        setResult({ queryKey, services: page?.content ?? [], error: null });
      })
      .catch((error) => {
        if (error?.name === 'AbortError') return;
        setResult({ queryKey, services: [], error });
      });

    return () => controller.abort();
  }, [shouldFetch, queryKey, roomType, serviceGroupId, departmentId]);

  if (!shouldFetch) return EMPTY;
  if (result.queryKey !== queryKey) return { services: [], loading: true, error: null };
  return { services: result.services, loading: false, error: result.error };
}

export default useDepartmentServices;
