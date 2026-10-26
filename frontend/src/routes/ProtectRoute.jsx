
import { Navigate, Outlet } from 'react-router';
import { useAuth } from '../hooks';

export default function ProtectRoute({ requiredRole }) {
    const { session } = useAuth();

    if (session.status === 'restoring') {
        return (
            <main className="app-loading">
                <p role="status">Đang tải…</p>
            </main>
        );
    }

    if (session.status !== 'authenticated' || !session.account) {
        return <Navigate to="/login" replace />;
    }

    if (
        requiredRole &&
        session.account.role !== requiredRole
    ) {
        return <Navigate to="/404" replace />;
    }

    return <Outlet />;
}
