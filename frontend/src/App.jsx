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
import Notifications from './pages/Notifications.jsx'
import PostPage from './pages/PostPage.jsx'
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
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { refreshSubscription } from './services/push.js'

/** Messages from the service worker: refresh badges on a push, follow notification taps, resubscribe. */
function usePushBridge(signedIn) {
  const navigate = useNavigate()
  useEffect(() => {
    if (!signedIn || !('serviceWorker' in navigator)) return
    refreshSubscription()
    const onMessage = (e) => {
      const { type, url } = e.data || {}
      if (type === 'techy:push') {
        window.dispatchEvent(new Event('techy:messages-changed'))
        window.dispatchEvent(new Event('techy:notifications-changed'))
      } else if (type === 'techy:open' && typeof url === 'string') {
        const u = new URL(url, window.location.origin)
        if (u.origin === window.location.origin) navigate(u.pathname + u.search)
      } else if (type === 'techy:resubscribe') {
        refreshSubscription()
      }
    }
    navigator.serviceWorker.addEventListener('message', onMessage)
    return () => navigator.serviceWorker.removeEventListener('message', onMessage)
  }, [signedIn, navigate])
}

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
  usePushBridge(Boolean(user))
  const routes = (
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/" element={<Protected><Home /></Protected>} />
      <Route path="/onboarding" element={<Protected><Onboarding /></Protected>} />
      <Route path="/people" element={<Protected><People /></Protected>} />
      <Route path="/messages" element={<Protected><Messages /></Protected>} />
      <Route path="/messages/:username" element={<Protected><Messages /></Protected>} />
      <Route path="/notifications" element={<Protected><Notifications /></Protected>} />
      <Route path="/post/:id" element={<Protected><PostPage /></Protected>} />
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
