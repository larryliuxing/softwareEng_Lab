import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { Shell } from './components/Shell'
import { Toast } from './components/Toast'
import { AttendancePage } from './pages/AttendancePage'
import { HomePage } from './pages/HomePage'
import { InvoicePage } from './pages/InvoicePage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { UnionPage } from './pages/UnionPage'
import { UsersPage } from './pages/UsersPage'
import { useStore } from './store'

function Gate() {
  const { session } = useStore()
  if (!session) return <Navigate to="/login" replace />
  return <Outlet />
}

export function App() {
  return (
    <>
      <Toast />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<Gate />}>
          <Route element={<Shell />}>
            <Route index element={<HomePage />} />
            <Route path="union" element={<UnionPage />} />
            <Route path="attendance" element={<AttendancePage />} />
            <Route path="invoice" element={<InvoicePage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="profile" element={<ProfilePage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}
