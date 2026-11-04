import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { AdminRoute, ProtectedRoute } from './auth/ProtectedRoute';
import { ConnectionBanner } from './components/ConnectionBanner';
import { ToastProvider } from './components/ToastProvider';
import { CommandPalette } from './components/CommandPalette';
import { AppLayout } from './layouts/AppLayout';

import { LoginPage } from './pages/Login';
import { RegisterPage } from './pages/Register';
import { ForgotPasswordPage } from './pages/ForgotPassword';
import { VerifyEmailPage } from './pages/VerifyEmail';
import { OverviewPage } from './pages/Overview';
import { AccountsPage } from './pages/Accounts';
import { CardsPage } from './pages/Cards';
import { TransferPage } from './pages/Transfer';
import { TransactionsPage } from './pages/Transactions';
import { BeneficiariesPage } from './pages/Beneficiaries';
import { SavingsPage } from './pages/Savings';
import { AnalyticsPage } from './pages/Analytics';
import { ScheduledPage } from './pages/Scheduled';
import { RequestsPage } from './pages/Requests';
import { NotificationsPage } from './pages/Notifications';
import { StatementsPage } from './pages/Statements';
import { SupportPage } from './pages/Support';
import { ProfilePage } from './pages/Profile';
import { SecurityPage } from './pages/Security';
import { SettingsPage } from './pages/Settings';
import { NotFoundPage } from './pages/NotFound';

import { AdminLayout } from './pages/admin/AdminLayout';
import { AdminOverview } from './pages/admin/AdminOverview';
import { AdminTransfers } from './pages/admin/AdminTransfers';
import { AdminUsers } from './pages/admin/AdminUsers';
import { AdminAudit } from './pages/admin/AdminAudit';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <ConnectionBanner />
          <CommandPalette />

          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            {/* Public on purpose: a verification link is opened in whatever browser
                the mail client hands it to, which usually has no session. */}
            <Route path="/verify-email" element={<VerifyEmailPage />} />

            <Route
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<OverviewPage />} />
              <Route path="/accounts" element={<AccountsPage />} />
              <Route path="/cards" element={<CardsPage />} />
              <Route path="/transfer" element={<TransferPage />} />
              <Route path="/transactions" element={<TransactionsPage />} />
              <Route path="/analytics" element={<AnalyticsPage />} />
              <Route path="/savings" element={<SavingsPage />} />
              <Route path="/scheduled" element={<ScheduledPage />} />
              <Route path="/requests" element={<RequestsPage />} />
              <Route path="/beneficiaries" element={<BeneficiariesPage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/statements" element={<StatementsPage />} />
              <Route path="/support" element={<SupportPage />} />
              <Route path="/security" element={<SecurityPage />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route path="/profile" element={<ProfilePage />} />

              <Route
                path="/admin"
                element={
                  <AdminRoute>
                    <AdminLayout />
                  </AdminRoute>
                }
              >
                <Route index element={<AdminOverview />} />
                <Route path="transfers" element={<AdminTransfers />} />
                <Route path="users" element={<AdminUsers />} />
                <Route path="audit" element={<AdminAudit />} />
              </Route>

              {/*
                A 404 inside the shell rather than a redirect to the dashboard.
                Silently sending somebody home hides the fact that their link was
                wrong, which is the one thing they need to know.
              */}
              <Route path="*" element={<NotFoundPage />} />
            </Route>

            {/* Unauthenticated and unmatched: the login page decides where next. */}
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
