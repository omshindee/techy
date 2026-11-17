import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import Loading from '../components/Loading.jsx'
import ReportDialog from '../components/ReportDialog.jsx'
import { ActionSheet } from '../components/Modal.jsx'
import { BackIcon, ChatIcon, MoreIcon, SendIcon } from '../components/Icons.jsx'
import { api } from '../services/api.js'
import { timeAgo } from '../utils/format.js'

const POLL_CHAT_MS = 4000   // while a chat is open and visible
const POLL_INBOX_MS = 15000
/** Tells the navigation badge and the inbox to refresh (messages were sent or read). */
export const notifyMessagesChanged = () => window.dispatchEvent(new Event('techy:messages-changed'))

/** Runs fn every ms while the tab is visible, and right away when it becomes visible again. */
function useVisiblePolling(fn, ms, active = true) {
  const ref = useRef(fn)
  ref.current = fn
  useEffect(() => {
    if (!active) return
    const tick = () => { if (document.visibilityState === 'visible') ref.current() }
    const t = setInterval(tick, ms)
    document.addEventListener('visibilitychange', tick)
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', tick) }
  }, [ms, active])
}

function dayLabel(iso) {
  const d = new Date(iso)
  const today = new Date()
  const yesterday = new Date(); yesterday.setDate(today.getDate() - 1)
  if (d.toDateString() === today.toDateString()) return 'Today'
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
}

const clock = (iso) => new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

function Inbox({ active }) {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.inbox().then((list) => { setItems(list); setError('') }).catch((e) => setError(e.message))
  }, [])
  useEffect(() => {
    load()
    window.addEventListener('techy:messages-changed', load)
    return () => window.removeEventListener('techy:messages-changed', load)
  }, [load])
  useVisiblePolling(load, POLL_INBOX_MS)

  return (
    <aside className="inbox" aria-label="Conversations">
      <h1 className="inbox-title">Messages</h1>
      {error && <p className="error small inbox-note">{error}</p>}
      {!items && !error && <Loading />}
      {items?.length === 0 && (
        <div className="inbox-empty">
          <ChatIcon size={36} />
          <p>No messages yet.</p>
          <p className="muted small">Open the profile of someone you follow and tap <strong>Message</strong>.</p>
          <Link to="/people" className="btn btn-ghost btn-small">Find people</Link>
        </div>
      )}
      <ul>
        {items?.map((c) => (
          <li key={c.id}>
            <Link to={`/messages/${c.other.username}`} className={`inbox-item ${active === c.other.username ? 'on' : ''} ${c.unread ? 'unread' : ''}`}>
              <Avatar username={c.other.username} url={c.other.avatarUrl} size={44} />
              <span className="inbox-text">
                <span className="inbox-row">
                  <strong className="ellipsis">{c.other.displayName}</strong>
                  <time className="mono muted small">{timeAgo(c.lastMessageAt)}</time>
                </span>
                <span className="inbox-row">
                  <span className="inbox-preview ellipsis">{c.lastMessageMine ? 'You: ' : ''}{c.lastMessage}</span>
                  {c.unread > 0 && <em className="count-badge" aria-label={`${c.unread} unread`}>{c.unread}</em>}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </aside>
  )
}

function Chat({ username }) {
  const navigate = useNavigate()
  const [thread, setThread] = useState(null)   // other, canSend, cannotSendReason, seenUpToId, hasOlder
  const [messages, setMessages] = useState([])
  const [error, setError] = useState('')
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [menuFor, setMenuFor] = useState(null)
  const [reporting, setReporting] = useState(null)
  const scroller = useRef(null)
  const input = useRef(null)
  const stickToBottom = useRef(true)
  const keepScrollFrom = useRef(null)
  const coarse = typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches

  // Load the chat whenever the person changes.
  useEffect(() => {
    let cancelled = false
    setThread(null); setMessages([]); setError(''); setText('')
    stickToBottom.current = true
    api.thread(username)
      .then((t) => { if (cancelled) return; setThread(t); setMessages(t.messages); notifyMessagesChanged() })
      .catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [username])

  const lastId = messages.length ? messages[messages.length - 1].id : null

  // New messages (and "Seen" updates) while the chat is open.
  useVisiblePolling(async () => {
    try {
      const t = await api.thread(username, lastId ? { after: lastId } : {})
      setThread((prev) => ({ ...prev, ...t, hasOlder: prev?.hasOlder ?? t.hasOlder }))
      if (t.messages.length) {
        setMessages((prev) => [...prev, ...t.messages.filter((m) => !prev.some((p) => p.id === m.id))])
        if (t.messages.some((m) => !m.mine)) notifyMessagesChanged()
      }
    } catch { /* the next tick retries; errors show on send */ }
  }, POLL_CHAT_MS, Boolean(thread))

  // Keep the view pinned to the newest message unless the user scrolled up to read.
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    if (keepScrollFrom.current !== null) {
      el.scrollTop = el.scrollHeight - keepScrollFrom.current
      keepScrollFrom.current = null
    } else if (stickToBottom.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages])

  function onScroll() {
    const el = scroller.current
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  async function loadOlder() {
    if (!messages.length) return
    setLoadingOlder(true)
    try {
      const t = await api.thread(username, { before: messages[0].id })
      keepScrollFrom.current = scroller.current.scrollHeight - scroller.current.scrollTop
      setMessages((prev) => [...t.messages, ...prev])
      setThread((prev) => ({ ...prev, hasOlder: t.hasOlder }))
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingOlder(false)
    }
  }

  async function send(e) {
    e?.preventDefault()
    const content = text.trim()
    if (!content || sending) return
    setSending(true)
    setError('')
    try {
      const m = await api.sendMessage(username, content)
      stickToBottom.current = true
      setMessages((prev) => (prev.some((p) => p.id === m.id) ? prev : [...prev, m]))
      setText('')
      notifyMessagesChanged()
      input.current?.focus()
    } catch (err) {
      setError(err.message)
    } finally {
      setSending(false)
    }
  }

  function onKeyDown(e) {
    // Desktop: Enter sends, Shift+Enter adds a line. Phones: the keyboard's Enter adds a line; use the send button.
    if (e.key === 'Enter' && !e.shiftKey && !coarse && !e.nativeEvent.isComposing) send(e)
  }

  // Grow the box with the text, up to a few lines.
  useLayoutEffect(() => {
    const el = input.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 140) + 'px'
  }, [text])

  if (error && !thread) {
    return (
      <section className="chat chat-center">
        <p className="muted">{error.includes('not found') ? 'This conversation isn’t available.' : error}</p>
        <Link to="/messages" className="btn btn-ghost btn-small">Back to messages</Link>
      </section>
    )
  }
  if (!thread) return <section className="chat chat-center"><Loading /></section>

  const other = thread.other
  const myLast = [...messages].reverse().find((m) => m.mine)

  return (
    <section className="chat" aria-label={`Chat with ${other.displayName}`}>
      <header className="chat-head">
        <button className="icon-button chat-back" onClick={() => navigate('/messages')} aria-label="Back to messages"><BackIcon size={22} /></button>
        <Link to={`/profile/${other.username}`} className="chat-who">
          <Avatar username={other.username} url={other.avatarUrl} size={36} />
          <span>
            <strong>{other.displayName}</strong>
            <span className="handle">@{other.username}</span>
          </span>
        </Link>
      </header>

      <div className="chat-scroll" ref={scroller} onScroll={onScroll}>
        {thread.hasOlder && (
          <button className="btn btn-quiet btn-small chat-older" onClick={loadOlder} disabled={loadingOlder}>
            {loadingOlder ? 'Loading…' : 'Load earlier messages'}
          </button>
        )}
        {messages.length === 0 && (
          <div className="chat-empty">
            <Avatar username={other.username} url={other.avatarUrl} size={64} />
            <p><strong>{other.displayName}</strong></p>
            <p className="muted small">{thread.canSend ? 'Say hi! Messages are private between the two of you.' : ''}</p>
          </div>
        )}
        {messages.map((m, i) => {
          const showDay = i === 0 || dayLabel(m.createdAt) !== dayLabel(messages[i - 1].createdAt)
          return (
            <div key={m.id} className="msg-wrap">
              {showDay && <div className="day-sep">{dayLabel(m.createdAt)}</div>}
              <div className={`msg ${m.mine ? 'mine' : ''}`}>
                <div className="msg-line">
                  <p className="msg-bubble">{m.content}</p>
                  {!m.mine && (
                    <button className="icon-button msg-more" onClick={() => setMenuFor(m)} aria-label="Message options"><MoreIcon size={16} /></button>
                  )}
                </div>
                <span className="msg-meta">
                  {clock(m.createdAt)}
                  {myLast && m.id === myLast.id && <> · {thread.seenUpToId && thread.seenUpToId >= m.id ? 'Seen' : 'Sent'}</>}
                </span>
              </div>
            </div>
          )
        })}
      </div>

      {thread.canSend ? (
        <form className="chat-compose" onSubmit={send}>
          <textarea ref={input} rows={1} value={text} onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown}
            placeholder={`Message ${other.displayName}…`} maxLength={2000} aria-label="Write a message" enterKeyHint={coarse ? 'enter' : 'send'} />
          <button className="btn btn-accent chat-send" disabled={sending || !text.trim()} aria-label="Send">
            <SendIcon size={18} />
          </button>
        </form>
      ) : (
        <div className="chat-locked">
          <span>{thread.cannotSendReason}</span>
          {thread.cannotSendReason?.startsWith('Follow') && (
            <Link to={`/profile/${other.username}`} className="btn btn-accent btn-small">View profile</Link>
          )}
        </div>
      )}
      {error && <p className="error small chat-error" role="alert">{error}</p>}

      {menuFor && (
        <ActionSheet onClose={() => setMenuFor(null)} options={[
          { label: 'Report message', danger: true, onClick: () => setReporting(menuFor.id) },
        ]} />
      )}
      {reporting && <ReportDialog targetType="MESSAGE" targetId={reporting} onClose={() => setReporting(null)} />}
    </section>
  )
}

/**
 * Direct messages. Wide screens show the inbox and the open chat side by side; phones show one
 * at a time (/messages = inbox, /messages/:username = chat).
 */
export default function Messages() {
  const { username } = useParams()
  return (
    <div className={`messages-page ${username ? 'has-chat' : ''}`}>
      <Inbox active={username} />
      {username
        ? <Chat key={username} username={username} />
        : (
          <section className="chat chat-center chat-placeholder">
            <ChatIcon size={40} />
            <p><strong>Your messages</strong></p>
            <p className="muted small">Pick a conversation, or message someone you follow from their profile.</p>
          </section>
        )}
    </div>
  )
}
