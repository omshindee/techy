import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const { login } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (password !== confirm) return setError('The two passwords don’t match.')
    setBusy(true)
    setError('')
    try {
      const res = await api.resetPassword(token, password)
      login(res.token, res.user)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Reset your password" footer={<Link to="/forgot-password">Request a new link</Link>}>
        <p className="error">This link is missing its code. Open the link from the email again, or request a new one.</p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Choose a new password" footer={<Link to="/login">Back to sign in</Link>}>
      <form onSubmit={submit} className="form">
        <p className="muted">This signs you out everywhere else.</p>
        <label className="field">
          <span>New password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72}
            autoComplete="new-password" required autoFocus />
          <span className="hint">At least 8 characters.</span>
        </label>
        <label className="field">
          <span>Repeat new password</span>
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} minLength={8} maxLength={72}
            autoComplete="new-password" required />
        </label>
        {error && (
          <p className="error" role="alert">
            {error} {error.includes('expired') && <Link to="/forgot-password">Request a new link</Link>}
          </p>
        )}
        <button className="btn btn-accent btn-block" disabled={busy}>{busy ? 'Saving…' : 'Set new password'}</button>
      </form>
    </AuthLayout>
  )
}
