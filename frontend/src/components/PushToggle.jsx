import { useEffect, useState } from 'react'
import { disablePush, enablePush, pushState } from '../services/push.js'

const HELP = {
  'ios-install': 'On iPhone and iPad, notifications work in the Techy app: tap Share → Add to Home Screen, open Techy from your Home Screen, then turn this on.',
  unsupported: 'This browser can’t show notifications from websites.',
  unavailable: 'Notifications aren’t available right now.',
  denied: 'Notifications are blocked for Techy. Allow them in your browser or phone settings (site settings → Notifications), then come back here.',
}

/**
 * "Notifications on this device": messages, follows, likes and comments, even when Techy is closed.
 * {@code compact}: a one-line prompt (Notifications page) that hides itself unless it can be turned on.
 */
export default function PushToggle({ compact = false }) {
  const [state, setState] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { pushState().then(setState).catch(() => setState('unsupported')) }, [])

  async function toggle() {
    setBusy(true)
    setError('')
    try {
      setState(state === 'on' ? await disablePush() : await enablePush())
    } catch (e) {
      setError(e.message || 'Couldn’t change notifications. Try again.')
    } finally {
      setBusy(false)
    }
  }

  if (!state) return null

  if (compact) {
    if (state !== 'off' && state !== 'ios-install') return null
    return (
      <div className="card push-prompt">
        <p><strong>Get notified when you’re away</strong>
          <span className="muted small">{state === 'ios-install' ? HELP['ios-install'] : 'New messages, followers, likes and comments, even when Techy is closed.'}</span>
        </p>
        {state === 'off' && <button className="btn btn-accent btn-small" onClick={toggle} disabled={busy}>{busy ? 'Turning on…' : 'Turn on'}</button>}
        {error && <p className="error small">{error}</p>}
      </div>
    )
  }

  return (
    <section className="form-section">
      <h2>Notifications on this device</h2>
      <p className="muted small">New messages, followers, likes and comments, even when Techy is closed.</p>
      {(state === 'on' || state === 'off') ? (
        <div className="row-actions">
          <span className={`badge ${state === 'on' ? 'ok' : ''}`}>{state === 'on' ? 'On' : 'Off'}</span>
          <button className={`btn btn-small ${state === 'on' ? 'btn-ghost' : 'btn-accent'}`} onClick={toggle} disabled={busy}>
            {busy ? 'Please wait…' : state === 'on' ? 'Turn off' : 'Turn on'}
          </button>
        </div>
      ) : <p className="small">{HELP[state]}</p>}
      {error && <p className="error small" role="alert">{error}</p>}
    </section>
  )
}
