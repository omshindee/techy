import { useState } from 'react'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

/** Shown until you confirm your email (when the server requires it for posting and following). */
export default function VerifyBanner() {
  const { user, config, refresh } = useAuth()
  const [state, setState] = useState('')
  const [busy, setBusy] = useState(false)
  if (!config.emailVerificationRequired || user.emailVerified !== false) return null

  async function resend() {
    setBusy(true)
    try {
      const res = await api.resendVerification()
      setState(res.message)
    } catch (e) {
      setState(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="verify-banner" role="status">
      <span>
        <strong>Confirm your email</strong> to post, comment, like and follow. We sent a link to your inbox.
      </span>
      <span className="verify-actions">
        {state ? <span className="small">{state}</span> : (
          <button className="btn btn-small btn-ghost" onClick={resend} disabled={busy}>{busy ? 'Sending…' : 'Resend link'}</button>
        )}
        <button className="btn btn-small btn-quiet" onClick={refresh}>I’ve confirmed it</button>
      </span>
    </div>
  )
}
