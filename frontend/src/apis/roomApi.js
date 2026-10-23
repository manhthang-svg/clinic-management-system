import { apiClient } from '../services/session';

export default function roomApi(client = apiClient) {
    return {
        // UC-17: Tạo phòng
        createRoom: (form) =>
            client.request('/rooms', {
                method: 'POST',
                body: form,
            }),
    };
}
