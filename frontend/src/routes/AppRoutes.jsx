
import { Navigate, Route, Routes } from 'react-router';

import { ROUTES } from './routes.js';

import {
    AccountManagementPage,
    DepartmentManagementPage,
    RoomManagementPage,
    ServiceManagementPage,
} from '../pages/admin/index.js';

import DashboardLayout from '../layouts/DashboardlLayout.jsx';
import ProtectRoute from './ProtectRoute.jsx';

import CashierOverviewPage from '../pages/cashier/CashierOverviewPage.jsx';
import PaymentManagementPage from '../pages/cashier/PaymentManagementPage.jsx';
import PaymentDetailPage from '../pages/cashier/PaymentDetailPage.jsx';
import PaymentHistoryPage from '../pages/cashier/PaymentHistoryPage.jsx';
import HomeRedirect from './HomeRedirect.jsx';

export function AppRoutes({
    session,
    account,
    notice,
    onLogout,
    onChangePassword,
}) {
    return (
        <Routes>
            <Route
                path="/"
                element={<HomeRedirect session={session} />}
            />

            {/* ADMIN */}
            <Route
                element={
                    <ProtectRoute
                        session={session}
                        requiredRole="ADMIN"
                    />
                }
            >
                <Route path="/admin">
                    <Route
                        index
                        element={
                            <Navigate
                                to={ROUTES.ADMIN.ACCOUNTS}
                                replace
                            />
                        }
                    />

                    <Route
                        path="accounts/*"
                        element={
                            <AccountManagementPage
                                currentAdmin={session.account}
                                notice={notice}
                                accountApi={account}
                                onLogout={onLogout}
                                onChangePassword={onChangePassword}
                            />
                        }
                    />

                    <Route
                        path="departments"
                        element={
                            <DepartmentManagementPage
                                currentAdmin={session.account}
                                onLogout={onLogout}
                                onChangePassword={onChangePassword}
                            />
                        }
                    />

                    <Route
                        path="rooms"
                        element={
                            <RoomManagementPage
                                currentAdmin={session.account}
                                onLogout={onLogout}
                                onChangePassword={onChangePassword}
                            />
                        }
                    />

                    <Route
                        path="services/*"
                        element={
                            <ServiceManagementPage
                                currentAdmin={session.account}
                                onLogout={onLogout}
                                onChangePassword={onChangePassword}
                            />
                        }
                    />
                </Route>
            </Route>

            {/* CASHIER */}
            <Route
                element={
                    <ProtectRoute
                        session={session}
                        requiredRole="CASHIER"
                    />
                }
            >
                <Route
                    path="/cashier"
                    element={<DashboardLayout />}
                >
                    <Route
                        index
                        element={<CashierOverviewPage />}
                    />

                    <Route
                        path="payments"
                        element={<PaymentManagementPage />}
                    />

                    <Route
                        path="payments/:id"
                        element={<PaymentDetailPage />}
                    />

                    <Route
                        path="payment-history"
                        element={<PaymentHistoryPage />}
                    />
                </Route>
            </Route>

            <Route
                path="/404"
                element={<div>404 - Không tìm thấy trang</div>}
            />

            {/* Các đường dẫn không tồn tại */}
            <Route
                path="*"
                element={<Navigate to="/404" replace />}
            />
        </Routes>
    );
}

export default AppRoutes;
