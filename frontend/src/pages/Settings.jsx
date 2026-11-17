import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import ProfileForm from '../components/ProfileForm.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { InstallCard } from '../components/InstallPrompt.jsx'

function EmailStatus() {
  const { user, refresh } = useAuth()
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)

  async function resend() {
    setBusy(true)
    try {
      setMsg((await api.resendVerification()).message)
    } catch (e) {
      setMsg(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="form-section">
      <h2>Email</h2>
      {user.emailVerified ? (
        <p><span className="badge ok">✓ Confirmed</span></p>
      ) : (
        <>
          <p><span className="badge warn">Not confirmed</span> Confirm your email to post, comment, like and follow.</p>
          <div className="row-actions">
            <button className="btn btn-ghost btn-small" onClick={resend} disabled={busy}>{busy ? 'Sending…' : 'Send a new link'}</button>
            <button className="btn btn-quiet btn-small" onClick={refresh}>I’ve confirmed it</button>
          </div>
          {msg && <p className="small muted">{msg}</p>}
        </>
      )}
      <p className="hint">Forgot your password? Sign out and use “Forgot password?” on the sign-in page.</p>
    </section>
  )
}

function BlockedAccounts() {
  const [list, setList] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.blocked().then(setList).catch((e) => setError(e.message))
  }, [])

  async function unblock(u) {
    try {
      await api.unblock(u.username)
      setList((prev) => prev.filter((x) => x.id !== u.id))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="form-section">
      <h2>Blocked accounts</h2>
      <p className="hint">Blocked people can’t see your profile, posts or comments, follow you, or interact with your posts. They aren’t told.</p>
      {error && <p className="error">{error}</p>}
      {list?.length === 0 && <p className="muted small">You haven’t blocked anyone.</p>}
      {list?.map((u) => (
        <div key={u.id} className="person compact">
          <Avatar username={u.username} url={u.avatarUrl} size={36} />
          <div className="person-text">
            <span className="person-name">
              <Link to={`/profile/${u.username}`} className="username">{u.displayName || u.username}</Link>
              <span className="handle">@{u.username}</span>
            </span>
          </div>
          <button className="btn btn-ghost btn-small" onClick={() => unblock(u)}>Unblock</button>
        </div>
      ))}
    </section>
  )
}

export default function Settings() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="narrow">
      <h1 className="page-title">Settings</h1>
      <div className="card form-card">
        <ProfileForm initial={user}
          extraAction={<Link to={`/profile/${user.username}`} className="btn btn-quiet">Back to profile</Link>} />
      </div>
      <div className="card form-card stack-gap">
        <div className="form">
          <EmailStatus />
          <InstallCard />
          <BlockedAccounts />
        </div>
      </div>
      <div className="signout-row">
        <button className="btn btn-ghost" onClick={() => { logout(); navigate('/login') }}>Sign out</button>
      </div>
      {/* eslint-disable-next-line no-undef */}
      <p className="build-stamp mono muted small">Techy build {__APP_BUILD__}</p>
    </div>
  )
}
