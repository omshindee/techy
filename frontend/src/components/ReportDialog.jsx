import { useState } from 'react'
import Modal from './Modal.jsx'
import { api } from '../services/api.js'

const REASONS = [
  ['SPAM', 'Spam', 'Ads, scams, fake engagement, repeated junk'],
  ['HARASSMENT', 'Harassment or bullying', 'Targeting or threatening someone'],
  ['HATE', 'Hate speech', 'Attacks on people for who they are'],
  ['NUDITY', 'Nudity or sexual content', ''],
  ['VIOLENCE', 'Violence or dangerous acts', ''],
  ['SCAM', 'Fraud or impersonation', 'Pretending to be someone else, phishing'],
  ['OTHER', 'Something else', ''],
]

const NOUN = { POST: 'post', COMMENT: 'comment', USER: 'account' }

/** "Report this post / comment / account" form. Reports go to the moderators' queue. */
export default function ReportDialog({ targetType, targetId, onClose }) {
  const [reason, setReason] = useState('')
  const [details, setDetails] = useState('')
  const [done, setDone] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (!reason) return setError('Pick a reason.')
    setBusy(true)
    setError('')
    try {
      const res = await api.report(targetType, targetId, reason, details.trim() || undefined)
      setDone(res.message)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Report ${NOUN[targetType]}`} onClose={onClose} className="dialog">
      {done ? (
        <div className="dialog-body">
          <p className="notice ok"><strong>✓ Report sent</strong>{done}</p>
          <p className="muted small">Reports are private: the person you reported isn’t told who reported them.</p>
          <div className="dialog-actions"><button className="btn btn-accent" onClick={onClose}>Done</button></div>
        </div>
      ) : (
        <form className="dialog-body" onSubmit={submit}>
          <fieldset className="reasons">
            <legend>Why are you reporting this {NOUN[targetType]}?</legend>
            {REASONS.map(([value, label, hint]) => (
              <label key={value} className={reason === value ? 'on' : ''}>
                <input type="radio" name="reason" value={value} checked={reason === value} onChange={() => setReason(value)} />
                <span><strong>{label}</strong>{hint && <small>{hint}</small>}</span>
              </label>
            ))}
          </fieldset>
          <label className="field">
            <span>Anything else moderators should know? <span className="muted">(optional)</span></span>
            <textarea rows={3} maxLength={500} value={details} onChange={(e) => setDetails(e.target.value)} />
          </label>
          {error && <p className="error" role="alert">{error}</p>}
          <div className="dialog-actions">
            <button type="button" className="btn btn-quiet" onClick={onClose}>Cancel</button>
            <button className="btn btn-accent" disabled={busy}>{busy ? 'Sending…' : 'Send report'}</button>
          </div>
        </form>
      )}
    </Modal>
  )
}
