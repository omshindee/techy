import { Navigate, Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar.jsx'
import Loading from './components/Loading.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Home from './pages/Home.jsx'
import Profile from './pages/Profile.jsx'
import CreatePost from './pages/CreatePost.jsx'
import People from './pages/People.jsx'
import Messages from './pages/Messages.jsx'
import Onboarding from './pages/Onboarding.jsx'
import Settings from './pages/Settings.jsx'
import ForgotPassword from './pages/ForgotPassword.jsx'
import ResetPassword from './pages/ResetPassword.jsx'
import VerifyEmail from './pages/VerifyEmail.jsx'
import Moderation from './pages/Moderation.jsx'
import VerifyBanner from './components/VerifyBanner.jsx'
import OfflineBanner from './components/OfflineBanner.jsx'
import PwaUpdater from './components/PwaUpdater.jsx'
import { useAuth } from './context/AuthContext.jsx'

function Protected({ children }) {
  const { user, ready } = useAuth()
  if (!ready) return <Loading />
  return user ? children : <Navigate to="/login" replace />
}

function GuestOnly({ children }) {
  const { user } = useAuth()
  return user ? <Navigate to="/" replace /> : children
}

export default function App() {
  const { user } = useAuth()
  const routes = (
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/" element={<Protected><Home /></Protected>} />
      <Route path="/onboarding" element={<Protected><Onboarding /></Protected>} />
      <Route path="/people" element={<Protected><People /></Protected>} />
      <Route path="/messages" element={<Protected><Messages /></Protected>} />
      <Route path="/messages/:username" element={<Protected><Messages /></Protected>} />
      <Route path="/new" element={<Protected><CreatePost /></Protected>} />
      <Route path="/settings" element={<Protected><Settings /></Protected>} />
      <Route path="/moderation" element={<Protected><Moderation /></Protected>} />
      <Route path="/forgot-password" element={<GuestOnly><ForgotPassword /></GuestOnly>} />
      {/* Email links must work whether or not you're signed in on this device. */}
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/profile/:username" element={<Protected><Profile /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )

  if (!user) {
    return (
      <>
        <OfflineBanner />
        <main className="guest">{routes}</main>
        <PwaUpdater />
      </>
    )
  }
  return (
    <div className="shell">
      <Navbar />
      <main className="main">
        <OfflineBanner />
        <VerifyBanner />
        {routes}
      </main>
      <PwaUpdater />
    </div>
  )
}
