import { useEffect, useState } from 'react'
import { api } from '../services/api.js'
import { disablePush, enablePush, pushState, refreshSubscription } from '../services/push.js'

const HELP = {
  'ios-install': 'On iPhone and iPad, notifications work in the Techy app: tap Share → Add to Home Screen, open Techy from your Home Screen, then turn this on.',
  unsupported: 'This browser can’t show notifications from websites.',
  unavailable: 'Notifications aren’t available right now. Reload the app and try again.',
  'no-server-key': 'Notifications aren’t switched on for the server yet (its push keys are missing).',
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

  const [test, setTest] = useState(null)
  async function sendTest() {
    setBusy(true)
    setTest(null)
    setError('')
    try {
      await refreshSubscription() // make sure the server knows this device
      setTest(await api.pushTest())
    } catch (e) {
      setError(e.message)
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
          {state === 'on' && <button className="btn btn-small btn-quiet" onClick={sendTest} disabled={busy}>Send a test</button>}
        </div>
      ) : <p className="small">{HELP[state]}</p>}
      {test && <TestResult results={test} />}
      {error && <p className="error small" role="alert">{error}</p>}
    </section>
  )
}

const SERVICE = (host) => host.includes('google') ? 'Google' : host.includes('apple') ? 'Apple'
  : host.includes('mozilla') ? 'Mozilla' : host.includes('windows') || host.includes('microsoft') ? 'Microsoft' : host

/** What each push service said about the test notification. */
function TestResult({ results }) {
  if (results.length === 0) {
    return <p className="small error">The server has no devices registered for your account. Turn notifications off and on again.</p>
  }
  return (
    <ul className="small push-results">
      {results.map((r, i) => (
        <li key={i}>
          {r.status >= 200 && r.status < 300
            ? <>✓ Sent via {SERVICE(r.service)}. It should appear within a few seconds; if it doesn’t, check that notifications for this app are allowed in your phone’s settings.</>
            : <span className="error">✗ {SERVICE(r.service) || 'Push service'} refused it{r.status ? ` (HTTP ${r.status})` : ''}{r.detail ? `: ${r.detail}` : ''}</span>}
        </li>
      ))}
    </ul>
  )
}
