import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import Captcha from '../components/Captcha.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

export default function Register() {
  const { login, config } = useAuth()
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [website, setWebsite] = useState('') // honeypot: hidden from people, bots fill it in
  const [captchaToken, setCaptchaToken] = useState('')
  const [captchaRound, setCaptchaRound] = useState(0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const needsCaptcha = Boolean(config.captchaSiteKey)

  async function submit(e) {
    e.preventDefault()
    if (needsCaptcha && !captchaToken) return setError('Please complete the captcha.')
    setBusy(true)
    setError('')
    try {
      const res = await api.register(username, email, password, { displayName, website, captchaToken })
      login(res.token, res.user)
      navigate('/onboarding')
    } catch (err) {
      setError(err.message)
      setCaptchaRound((n) => n + 1) // captcha tokens are single-use
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Create your account" footer={<>Already on Techy? <Link to="/login">Sign in</Link></>}>
      <form onSubmit={submit} className="form">
        <label className="field">
          <span>Name</span>
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50}
            autoComplete="name" placeholder="e.g. Priya Sharma" autoFocus />
          <span className="hint">Optional. Shown on your posts, and people can find you by it.</span>
        </label>
        <label className="field">
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} minLength={3} maxLength={30}
            pattern="[A-Za-z0-9_\-]+" title="3–30 characters: letters, numbers, _ and -" autoComplete="username" required
            autoCapitalize="none" autoCorrect="off" spellCheck={false} />
          <span className="hint">Letters, numbers, _ and -. Your profile lives at /profile/{username || 'username'}.</span>
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
          {config.emailVerificationRequired && <span className="hint">We’ll send a link to confirm it.</span>}
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72}
            autoComplete="new-password" required />
          <span className="hint">At least 8 characters.</span>
        </label>
        {/* Honeypot: off-screen and skipped by keyboard and screen readers. */}
        <div className="hp" aria-hidden="true">
          <label>Website <input tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} /></label>
        </div>
        <Captcha siteKey={config.captchaSiteKey} onToken={setCaptchaToken} resetKey={captchaRound} onError={setError} />
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn btn-accent btn-block" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
      </form>
    </AuthLayout>
  )
}
