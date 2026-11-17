import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import Loading from '../components/Loading.jsx'
import { api, mediaUrl } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { timeAgo } from '../utils/format.js'

const REASON = {
  SPAM: 'Spam', HARASSMENT: 'Harassment', HATE: 'Hate speech', NUDITY: 'Nudity / sexual',
  VIOLENCE: 'Violence', SCAM: 'Fraud / impersonation', OTHER: 'Other',
}
const KIND = { POST: 'Post', COMMENT: 'Comment', USER: 'Account' }

function ReportCard({ report, onResolved }) {
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const t = report.target
  const open = report.status === 'OPEN'

  async function resolve(action, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return
    setBusy(action)
    setError('')
    try {
      onResolved(await api.resolveReport(report.id, action))
    } catch (e) {
      setError(e.message)
      setBusy('')
    }
  }

  return (
    <article className="card report">
      <header className="report-head">
        <span className="badge">{KIND[report.targetType]}</span>
        <span className="badge danger">{REASON[report.reason]}</span>
        <span className="muted small">
          reported by <Link to={`/profile/${report.reporter.username}`}>@{report.reporter.username}</Link> · {timeAgo(report.createdAt)}
        </span>
      </header>
      {report.details && <blockquote className="report-details">“{report.details}”</blockquote>}

      {t.exists ? (
        <div className="report-target">
          <div className="report-author">
            <Avatar username={t.author.username} url={t.author.avatarUrl} size={28} />
            <Link to={`/profile/${t.author.username}`} className="username">{t.author.displayName}</Link>
            <span className="handle">@{t.author.username}</span>
            {t.authorSuspended && <span className="badge danger">Suspended</span>}
          </div>
          {t.text && <p className="report-text">{t.text}</p>}
          {t.imageUrl && <img className="report-image" src={mediaUrl(t.imageUrl)} alt="Reported photo" />}
        </div>
      ) : (
        <p className="muted small report-target">This {KIND[report.targetType].toLowerCase()} has already been deleted.</p>
      )}

      {open ? (
        <div className="row-actions">
          {report.targetType !== 'USER' && t.exists && (
            <button className="btn btn-accent btn-small" disabled={!!busy}
              onClick={() => resolve('REMOVE', `Delete this ${KIND[report.targetType].toLowerCase()}? This can’t be undone.`)}>
              {busy === 'REMOVE' ? 'Removing…' : `Remove ${KIND[report.targetType].toLowerCase()}`}
            </button>
          )}
          {t.exists && !t.authorSuspended && (
            <button className="btn btn-ghost btn-small danger-text" disabled={!!busy}
              onClick={() => resolve('SUSPEND', `Suspend @${t.author.username}? They’ll be signed out and their profile and posts hidden.`)}>
              {busy === 'SUSPEND' ? 'Suspending…' : `Suspend @${t.author.username}`}
            </button>
          )}
          <button className="btn btn-quiet btn-small" disabled={!!busy} onClick={() => resolve('DISMISS')}>
            {busy === 'DISMISS' ? 'Dismissing…' : 'Dismiss'}
          </button>
        </div>
      ) : (
        <p className="small muted">
          <span className={`badge ${report.status === 'RESOLVED' ? 'ok' : ''}`}>{report.resolution}</span>
          {' '}by @{report.resolvedBy} · {timeAgo(report.resolvedAt)}
        </p>
      )}
      {error && <p className="error small">{error}</p>}
    </article>
  )
}

/** Moderators' queue: open reports first, with one-click remove / suspend / dismiss. */
export default function Moderation() {
  const { user } = useAuth()
  const [status, setStatus] = useState('open')
  const [reports, setReports] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user.admin) return
    let cancelled = false
    setReports(null)
    api.reports(status).then((r) => !cancelled && setReports(r)).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [status, user.admin])

  if (!user.admin) return <Navigate to="/" replace />

  // A decision can settle several reports about the same thing, so drop them all from the open list.
  const resolved = (r) => {
    setReports((prev) => status === 'open'
      ? prev.filter((x) => !(x.targetType === r.targetType && x.targetId === r.targetId) && x.id !== r.id)
      : prev.map((x) => (x.id === r.id ? r : x)))
    window.dispatchEvent(new Event('techy:reports-changed')) // refresh the open-reports badge
  }

  return (
    <div className="narrow">
      <h1 className="page-title">Moderation</h1>
      <div className="channels-main" role="tablist">
        {[['open', 'Open'], ['closed', 'Handled']].map(([k, label]) => (
          <button key={k} role="tab" aria-selected={status === k} className={status === k ? 'on' : ''} onClick={() => setStatus(k)}>{label}</button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      {!reports && !error && <Loading />}
      {reports?.length === 0 && (
        <div className="card empty"><h2>{status === 'open' ? 'All clear' : 'Nothing handled yet'}</h2>
          {status === 'open' && <p className="muted">No open reports right now.</p>}</div>
      )}
      <div className="feed moderation-list">
        {reports?.map((r) => <ReportCard key={r.id} report={r} onResolved={resolved} />)}
      </div>
    </div>
  )
}
