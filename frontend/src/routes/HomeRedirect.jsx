import { Navigate } from 'react-router';

export default function HomeRedirect({ session }) {
    if (session.status === 'restoring') {
        return <p>Đang tải...</p>;
    }

    if (session.status === 'anonymous') {
        return <Navigate to="/login" replace />;
    }

    if (session.account?.role === 'CASHIER') {
        return <Navigate to="/cashier" replace />;
    }

    if (session.account?.role === 'ADMIN') {
        return <Navigate to="/admin/accounts" replace />;
    }

    if (session.account?.role === 'RECEPTIONIST') {
        return <Navigate to="/receptionist" replace />;
    }

    if (session.account?.role === 'DOCTOR') {
        return <Navigate to="/doctor" replace />;
    }

    if (session.account?.role === 'TECHNICIAN') {
        return <Navigate to="/technician" replace />;
    }

    return <p>Role không được hỗ trợ: {session.account?.role}</p>;
}
