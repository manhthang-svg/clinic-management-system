
import { Navigate, Outlet } from 'react-router';

export default function ProtectRoute({ session, requiredRole }) {
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
