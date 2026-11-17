import { apiClient } from '../services/session';

// UC-34: API benh nhan da kham cua bac si dang dang nhap
export function createExaminedPatientApi(client = apiClient) {
  return {
    list: ({ from, to, keyword, page, size } = {}, signal) => {
      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      if (keyword) params.set('keyword', keyword);
      if (page) params.set('page', String(page));
      if (size) params.set('size', String(size));
      const query = params.toString();
      return client.request(`/examined-patients${query ? `?${query}` : ''}`, { signal });
    },
  };
}

export const examinedPatientApi = createExaminedPatientApi();

export default examinedPatientApi;
