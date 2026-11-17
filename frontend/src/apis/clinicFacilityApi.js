import { apiClient } from '../services/session';

// UC-77: API xem / luu thong tin co so
export function createClinicFacilityApi(client = apiClient) {
  return {
    get: (signal) => client.request('/clinic-facility', { signal }),
    save: (body) => client.request('/clinic-facility', { method: 'PUT', body }),
  };
}

export const clinicFacilityApi = createClinicFacilityApi();

export default clinicFacilityApi;
