import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Avatar from './Avatar.jsx'
import ReportDialog from './ReportDialog.jsx'
import { ActionSheet } from './Modal.jsx'
import { MoreIcon } from './Icons.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { timeAgo } from '../utils/format.js'

/**
 * The discussion under a post: loads when opened, oldest first, with a reply box at the bottom.
 * Each comment has a menu: delete (your comment, a comment on your post, or as a moderator) or report.
 */
export default function CommentSection({ postId, postAuthorId, onAdded, onRemoved }) {
  const { user } = useAuth()
  const [comments, setComments] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [menuFor, setMenuFor] = useState(null)
  const [reporting, setReporting] = useState(null)

  useEffect(() => {
    let cancelled = false
    api.comments(postId)
      .then((list) => !cancelled && setComments(list))
      .catch((e) => !cancelled && setLoadError(e.message))
    return () => { cancelled = true }
  }, [postId])

  async function submit(e) {
    e.preventDefault()
    if (!text.trim() || busy) return
    setBusy(true)
    setError('')
    try {
      const c = await api.addComment(postId, text)
      setComments((prev) => [...(prev || []), c])
      setText('')
      onAdded?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove(c) {
    if (!window.confirm('Delete this comment?')) return
    try {
      await api.deleteComment(postId, c.id)
      setComments((prev) => prev.filter((x) => x.id !== c.id))
      onRemoved?.()
    } catch (err) {
      setError(err.message)
    }
  }

  async function block(c) {
    if (!window.confirm(`Block @${c.author.username}? You won’t see each other’s posts, comments or profiles.`)) return
    try {
      await api.block(c.author.username)
      setComments((prev) => prev.filter((x) => x.author.id !== c.author.id))
    } catch (err) {
      setError(err.message)
    }
  }

  function optionsFor(c) {
    const mine = c.author.id === user.id
    const canDelete = mine || postAuthorId === user.id || user.admin
    return [
      ...(canDelete ? [{ label: mine ? 'Delete comment' : 'Remove comment', danger: true, onClick: () => remove(c) }] : []),
      ...(!mine ? [
        { label: 'Report comment', danger: !canDelete, onClick: () => setReporting(c.id) },
        { label: `Block @${c.author.username}`, onClick: () => block(c) },
      ] : []),
    ]
  }

  return (
    <section className="thread" aria-label="Comments">
      {!comments && !loadError && <p className="muted small">Loading comments…</p>}
      {loadError && <p className="error small">{loadError}</p>}
      {comments?.length === 0 && <p className="muted small">No comments yet. Ask a question or share a tip.</p>}
      {comments?.length > 0 && (
        <ul>
          {comments.map((c) => (
            <li key={c.id}>
              <Link to={`/profile/${c.author.username}`} tabIndex={-1}>
                <Avatar username={c.author.username} url={c.author.avatarUrl} size={28} />
              </Link>
              <div className="bubble">
                <div className="bubble-head">
                  <Link to={`/profile/${c.author.username}`} className="username">{c.author.displayName || c.author.username}</Link>
                  <time dateTime={c.createdAt}>{timeAgo(c.createdAt)}</time>
                  <button className="icon-button bubble-more" onClick={() => setMenuFor(c)} aria-label="Comment options">
                    <MoreIcon size={16} />
                  </button>
                </div>
                <p>{c.content}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={submit} className="reply">
        <Avatar username={user.username} url={user.avatarUrl} size={28} />
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Write a comment…"
          maxLength={1000} aria-label="Write a comment" enterKeyHint="send" />
        {/* Keep focus in the input so the phone keyboard doesn't close and swallow the tap. */}
        <button className="btn btn-small" disabled={busy || !text.trim()} onMouseDown={(e) => e.preventDefault()}>Reply</button>
      </form>
      {error && <p className="error small">{error}</p>}
      {menuFor && <ActionSheet onClose={() => setMenuFor(null)} options={optionsFor(menuFor)} />}
      {reporting && <ReportDialog targetType="COMMENT" targetId={reporting} onClose={() => setReporting(null)} />}
    </section>
  )
}
