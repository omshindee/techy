import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import Captcha from '../components/Captcha.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

export default function ForgotPassword() {
  const { config } = useAuth()
  const [email, setEmail] = useState('')
  const [captchaToken, setCaptchaToken] = useState('')
  const [captchaRound, setCaptchaRound] = useState(0)
  const [sent, setSent] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (config.captchaSiteKey && !captchaToken) return setError('Please complete the captcha.')
    setBusy(true)
    setError('')
    try {
      const res = await api.forgotPassword(email, captchaToken)
      setSent(res.message)
    } catch (err) {
      setError(err.message)
      setCaptchaRound((n) => n + 1)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Reset your password" footer={<>Remembered it? <Link to="/login">Sign in</Link></>}>
      {sent ? (
        <div className="notice" role="status">
          <strong>Check your inbox</strong>
          <p>{sent}</p>
          <p className="muted small">The link works for 1 hour. Didn’t get it? Check spam, or try again in a few minutes.</p>
        </div>
      ) : (
        <form onSubmit={submit} className="form">
          <p className="muted">Enter the email you signed up with and we’ll send you a link to choose a new password.</p>
          <label className="field">
            <span>Email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus />
          </label>
          <Captcha siteKey={config.captchaSiteKey} onToken={setCaptchaToken} resetKey={captchaRound} onError={setError} />
          {error && <p className="error" role="alert">{error}</p>}
          <button className="btn btn-accent btn-block" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
        </form>
      )}
    </AuthLayout>
  )
}
