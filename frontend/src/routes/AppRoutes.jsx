import { Navigate, Route, Routes, useNavigate } from "react-router";

import { ROUTES } from "./routes.js";

import {
  AccountManagementPage,
  DepartmentManagementPage,
  ServiceManagementPage,
  ScheduleManagementPage,
  HospitalInformationPage,
} from "../pages/admin/index.js";

import DashboardLayout from "../layouts/DashboardlLayout.jsx";
import ProtectRoute from "./ProtectRoute.jsx";

import CashierOverviewPage from "../pages/cashier/CashierOverviewPage.jsx";
import PaymentManagementPage from "../pages/cashier/PaymentManagementPage.jsx";
import PaymentDetailPage from "../pages/cashier/PaymentDetailPage.jsx";
import PaymentHistoryPage from "../pages/cashier/PaymentHistoryPage.jsx";
import HomeRedirect from "./HomeRedirect.jsx";
import ReceptionistOverviewPage from "../pages/receptionist/ReceptionistOverviewPage.jsx";
import ReceptionistPatientsPage from "../pages/receptionist/ReceptionistPatientsPage.jsx";
import ReceptionistAppointmentsPage from "../pages/receptionist/ReceptionistAppointmentsPage.jsx";
import ReceptionistCreatePatientPage from "../pages/receptionist/ReceptionistCreatePatientPage.jsx";
import ReceptionistVisitRegisterPage from "../pages/receptionist/ReceptionistVisitRegisterPage.jsx";
import ReceptionistPatientDetailPage from "../pages/receptionist/ReceptionistPatientDetailPage.jsx";
import ReceptionistUpdatePatientPage from "../pages/receptionist/ReceptionistUpdatePatientPage.jsx";
import ReceptionistRoomQueuePage from "../pages/receptionist/ReceptionistRoomQueuePage.jsx";
import PaymentResultPage from "../pages/payment/PaymentResultPage.jsx";
import DoctorWaitingListPage from "../pages/doctor/DoctorWaitingListPage.jsx";
import ClinicalExaminationPage from "../pages/doctor/ClinicalExaminationPage.jsx";
import OwnProfilePage from "../pages/profile/OwnProfilePage.jsx";
import {
  TechnicianDashboardPage,
  TechnicianQueuePage,
  ParaclinicalResultListPage,
  ParaclinicalResultPage,
} from "../pages/technician/index.js";

export function AppRoutes({
  session,
  account,
  notice,
  onLogout,
  onChangePassword,
}) {
  const navigate = useNavigate();

  return (
    <Routes>
      <Route path="/" element={<HomeRedirect session={session} />} />

      {/* ADMIN */}
      <Route element={<ProtectRoute session={session} requiredRole="ADMIN" />}>
        <Route path="/admin">
          <Route
            index
            element={<Navigate to={ROUTES.ADMIN.ACCOUNTS} replace />}
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
            element={<Navigate to="/admin/departments" replace />}
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

          <Route
            path="schedules"
            element={
              <ScheduleManagementPage
                currentAdmin={session.account}
                onLogout={onLogout}
                onChangePassword={onChangePassword}
                onOpenProfile={() => navigate(ROUTES.ADMIN.PROFILE)}
              />
            }
          />

          <Route
            path="hospital-information"
            element={
              <HospitalInformationPage
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
        element={<ProtectRoute session={session} requiredRole="CASHIER" />}
      >
        <Route
          path="/cashier"
          element={
            <DashboardLayout
              currentUser={session.account}
              onLogout={onLogout}
              onChangePassword={onChangePassword}
              onOpenProfile={() => navigate(ROUTES.CASHIER.PROFILE)}
            />
          }
        >
          <Route index element={<CashierOverviewPage />} />

          <Route path="payments" element={<PaymentManagementPage />} />

          <Route path="payments/:id" element={<PaymentDetailPage />} />

          <Route path="payment-history" element={<PaymentHistoryPage />} />
          <Route
            path="schedules"
            element={
              <ScheduleManagementPage
                currentUser={session.account}
                readOnly={true}
              />
            }
          />
          <Route
            path="profile"
            element={
              <OwnProfilePage
                accountApi={account}
                onBack={() => navigate(ROUTES.CASHIER.OVERVIEW)}
                onChangePassword={onChangePassword}
              />
            }
          />
        </Route>
      </Route>

      {/* RECEPTIONIST */}
      <Route
        element={<ProtectRoute session={session} requiredRole="RECEPTIONIST" />}
      >
        <Route
          path="/receptionist"
          element={
            <DashboardLayout
              currentUser={session.account}
              onLogout={onLogout}
              onChangePassword={onChangePassword}
              onOpenProfile={() => navigate(ROUTES.RECEPTIONIST.PROFILE)}
            />
          }
        >
          {/* Tổng quan lễ tân */}
          <Route index element={<ReceptionistOverviewPage />} />

          {/* Quản lý bệnh nhân */}
          <Route path="patients" element={<ReceptionistPatientsPage />} />

          <Route
            path="patients/new"
            element={<ReceptionistCreatePatientPage />}
          />

          <Route
            path="patients/:id"
            element={<ReceptionistPatientDetailPage />}
          />

          <Route
            path="patients/:id/edit"
            element={<ReceptionistUpdatePatientPage />}
          />

          <Route
            path="patients/:id/register"
            element={<ReceptionistVisitRegisterPage />}
          />

          {/* Lịch khám */}
          <Route
            path="appointments"
            element={<ReceptionistAppointmentsPage />}
          />

          {/* Tình trạng hàng đợi phòng khám (UC-42) */}
          <Route path="room-queue" element={<ReceptionistRoomQueuePage />} />
          <Route
            path="schedules"
            element={
              <ScheduleManagementPage
                currentUser={session.account}
                readOnly={true}
              />
            }
          />
          <Route
            path="profile"
            element={
              <OwnProfilePage
                accountApi={account}
                onBack={() => navigate(ROUTES.RECEPTIONIST.OVERVIEW)}
                onChangePassword={onChangePassword}
              />
            }
          />
        </Route>
      </Route>

      {/* DOCTOR */}
      <Route element={<ProtectRoute session={session} requiredRole="DOCTOR" />}>
        <Route
          path="/doctor"
          element={
            <DashboardLayout
              currentUser={session.account}
              onLogout={onLogout}
              onChangePassword={onChangePassword}
              onOpenProfile={() => navigate(ROUTES.DOCTOR.PROFILE)}
            />
          }
        >
          <Route index element={<Navigate to="appointments" replace />} />
          <Route path="queue" element={<DoctorWaitingListPage />} />
          <Route path="examinations" element={<Navigate to="../appointments" replace />} />
          <Route path="appointments" element={<DoctorWaitingListPage />} />
          <Route path="conclusion" element={<DoctorWaitingListPage initialTab="conclusion" />} />
          <Route path="postponed" element={<DoctorWaitingListPage initialTab="skipped" />} />
          <Route path="clinical-examination" element={<ClinicalExaminationPage />} />
          <Route path="paraclinical-orders" element={<Navigate to="../appointments" replace />} />
          <Route path="room-transfers" element={<Navigate to="../appointments" replace />} />
          <Route
            path="skipped-patients"
            element={<DoctorWaitingListPage initialTab="skipped" />}
          />
          <Route path="medical-records" element={<Navigate to="../appointments" replace />} />
          <Route path="prescriptions" element={<Navigate to="../appointments" replace />} />
          <Route path="follow-ups" element={<Navigate to="../appointments" replace />} />
          <Route path="examined-patients" element={<Navigate to="../appointments" replace />} />
          <Route
            path="schedules"
            element={
              <ScheduleManagementPage
                currentUser={session.account}
                readOnly={true}
              />
            }
          />
          <Route
            path="profile"
            element={
              <OwnProfilePage
                accountApi={account}
                onBack={() => navigate(ROUTES.DOCTOR.OVERVIEW)}
                onChangePassword={onChangePassword}
              />
            }
          />
        </Route>
      </Route>

      {/* TECHNICIAN */}
      <Route
        element={<ProtectRoute session={session} requiredRole="TECHNICIAN" />}
      >
        <Route
          path="/technician"
          element={
            <DashboardLayout
              currentUser={session.account}
              onLogout={onLogout}
              onChangePassword={onChangePassword}
              onOpenProfile={() => navigate(ROUTES.TECHNICIAN.PROFILE)}
            />
          }
        >
          <Route index element={<TechnicianDashboardPage />} />
          <Route path="dashboard" element={<TechnicianDashboardPage />} />
          <Route path="queue" element={<TechnicianQueuePage />} />
          <Route path="results" element={<ParaclinicalResultListPage />} />
          <Route path="results/:orderId" element={<ParaclinicalResultPage />} />
          <Route
            path="profile"
            element={
              <OwnProfilePage
                accountApi={account}
                onBack={() => navigate(ROUTES.TECHNICIAN.OVERVIEW)}
                onChangePassword={onChangePassword}
              />
            }
          />
          <Route
            path="schedules"
            element={
              <ScheduleManagementPage
                currentUser={session.account}
                readOnly={true}
              />
            }
          />
        </Route>
      </Route>

      {/* KẾT QUẢ THANH TOÁN PAYOS */}
      <Route path="/payment/result" element={<PaymentResultPage />} />
      <Route path="/payment/success" element={<PaymentResultPage />} />
      <Route path="/payment/cancel" element={<PaymentResultPage />} />

      <Route path="/404" element={<div>404 - Không tìm thấy trang</div>} />

      {/* Các đường dẫn không tồn tại */}
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}

export default AppRoutes;
