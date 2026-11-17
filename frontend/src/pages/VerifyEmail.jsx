import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import Loading from '../components/Loading.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

/** Opened from the confirmation email. Works whether or not you're signed in on this device. */
export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const { user, refresh } = useAuth()
  const [state, setState] = useState({ status: 'working', message: '' })
  const sent = useRef(false) // StrictMode runs effects twice in dev; links are single-use

  useEffect(() => {
    if (sent.current) return
    sent.current = true
    if (!token) return setState({ status: 'error', message: 'This link is missing its code. Open the link from the email again.' })
    api.verifyEmail(token)
      .then((res) => { setState({ status: 'done', message: res.message }); refresh() })
      .catch((e) => setState({ status: 'error', message: e.message }))
  }, [token, refresh])

  const next = user ? <Link to="/" className="btn btn-accent">Go to my feed</Link> : <Link to="/login" className="btn btn-accent">Sign in</Link>

  return (
    <AuthLayout title="Confirm your email" footer={null}>
      {state.status === 'working' && <Loading label="Confirming" />}
      {state.status === 'done' && (
        <div className="notice ok" role="status">
          <strong>✓ Email confirmed</strong>
          <p>{state.message} You can now post, comment and follow people.</p>
          {next}
        </div>
      )}
      {state.status === 'error' && (
        <div className="notice" role="alert">
          <strong>That didn’t work</strong>
          <p>{state.message}</p>
          {user
            ? <p className="muted small">You can send yourself a fresh link from <Link to="/settings">Settings</Link>.</p>
            : <p className="muted small">Sign in and send yourself a fresh link from Settings.</p>}
          {next}
        </div>
      )}
    </AuthLayout>
  )
}
