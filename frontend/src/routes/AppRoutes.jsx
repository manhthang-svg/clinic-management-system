
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
import ReceptionistOverviewPage from '../pages/receptionist/ReceptionistOverviewPage.jsx';
import ReceptionistPatientsPage from '../pages/receptionist/ReceptionistPatientsPage.jsx';
import ReceptionistAppointmentsPage from '../pages/receptionist/ReceptionistAppointmentsPage.jsx';
import ReceptionistCreatePatientPage from '../pages/receptionist/ReceptionistCreatePatientPage.jsx';

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
                        path="profile"
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
                    element={
                        <DashboardLayout
                            currentUser={session.account}
                            onLogout={onLogout}
                            onChangePassword={onChangePassword}
                        />
                    }
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

            {/* RECEPTIONIST */}
            <Route
                element={
                    <ProtectRoute
                        session={session}
                        requiredRole="RECEPTIONIST"
                    />
                }
            >
                <Route
                    path="/receptionist"
                    element={
                        <DashboardLayout
                            currentUser={session.account}
                            onLogout={onLogout}
                            onChangePassword={onChangePassword}
                        />
                    }
                >
                    {/* Tổng quan lễ tân */}
                    <Route
                        index
                        element={<ReceptionistOverviewPage />}
                    />

                    {/* Quản lý bệnh nhân */}
                    <Route
                        path="patients"
                        element={<ReceptionistPatientsPage />}
                    />

                    <Route
                        path="patients/new"
                        element={<ReceptionistCreatePatientPage />}
                    />

                    {/* Lịch khám */}
                    <Route
                        path="appointments"
                        element={<ReceptionistAppointmentsPage />}
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
