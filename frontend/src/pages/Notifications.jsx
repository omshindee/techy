import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import Loading from '../components/Loading.jsx'
import PushToggle from '../components/PushToggle.jsx'
import { BellIcon } from '../components/Icons.jsx'
import { api, mediaUrl } from '../services/api.js'
import { timeAgo } from '../utils/format.js'

/** Tells the navigation badge to refresh. */
const notifyChanged = () => window.dispatchEvent(new Event('techy:notifications-changed'))

const TEXT = {
  FOLLOW: 'started following you.',
  LIKE: 'liked your post.',
  COMMENT: 'commented on your post:',
}

function FollowBack({ n, onFollowed }) {
  const [busy, setBusy] = useState(false)
  if (n.followingActor) return <span className="btn btn-ghost btn-small notif-following" aria-disabled="true">Following</span>
  return (
    <button className="btn btn-accent btn-small" disabled={busy} onClick={async (e) => {
      e.preventDefault(); e.stopPropagation()
      setBusy(true)
      try { await api.follow(n.actor.username); onFollowed(n.actor.id) } catch { setBusy(false) }
    }}>Follow back</button>
  )
}

function Row({ n, onFollowed }) {
  const navigate = useNavigate()
  const to = n.postId ? `/post/${n.postId}` : `/profile/${n.actor.username}`
  const name = n.actor.displayName || n.actor.username
  return (
    <li>
      {/* The whole row opens the post (or the profile, for follows); name and photo open the profile. */}
      <div className={`notif ${n.read ? '' : 'unread'}`} role="link" tabIndex={0}
        onClick={() => navigate(to)} onKeyDown={(e) => { if (e.key === 'Enter') navigate(to) }}>
        <Link to={`/profile/${n.actor.username}`} onClick={(e) => e.stopPropagation()} tabIndex={-1}>
          <Avatar username={n.actor.username} url={n.actor.avatarUrl} size={44} />
        </Link>
        <p className="notif-text">
          <Link to={`/profile/${n.actor.username}`} className="username" onClick={(e) => e.stopPropagation()}>{name}</Link>
          {' '}{TEXT[n.type]}
          {n.snippet && <span className="notif-snippet"> “{n.snippet}”</span>}
          <time className="notif-time" dateTime={n.createdAt}> {timeAgo(n.createdAt)}</time>
        </p>
        {n.type === 'FOLLOW'
          ? <FollowBack n={n} onFollowed={onFollowed} />
          : n.postImageUrl
            ? <img className="notif-thumb" src={mediaUrl(n.postImageUrl)} alt="" loading="lazy" />
            : n.postText && <span className="notif-thumb notif-thumb-text" aria-hidden="true">{n.postText}</span>}
      </div>
    </li>
  )
}

/** Who followed you, liked your posts or commented on them. Opening the page marks everything read. */
export default function Notifications() {
  const [items, setItems] = useState(null)
  const [next, setNext] = useState(null)
  const [error, setError] = useState('')
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.notifications().then((page) => {
      if (cancelled) return
      setItems(page.items)
      setNext(page.nextBefore)
      // Keep the "new" highlight for this visit, but clear the badge.
      if (page.items.some((n) => !n.read)) api.markNotificationsRead().then(notifyChanged).catch(() => {})
    }).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [])

  async function loadMore() {
    setLoadingMore(true)
    try {
      const page = await api.notifications(next)
      setItems((prev) => [...prev, ...page.items])
      setNext(page.nextBefore)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingMore(false)
    }
  }

  const followed = (actorId) =>
    setItems((prev) => prev.map((n) => (n.actor.id === actorId ? { ...n, followingActor: true } : n)))

  const fresh = items?.filter((n) => !n.read) ?? []
  const earlier = items?.filter((n) => n.read) ?? []

  return (
    <div className="narrow">
      <h1 className="page-title">Notifications</h1>
      <PushToggle compact />
      {error && <p className="error" role="alert">{error}</p>}
      {!items && !error && <Loading />}
      {items?.length === 0 && (
        <div className="card empty">
          <BellIcon size={36} />
          <h2>Nothing yet</h2>
          <p className="muted">When people follow you, like your posts or comment on them, you’ll see it here.</p>
        </div>
      )}
      {items?.length > 0 && (
        <section className="card notif-card">
          {fresh.length > 0 && <>
            <h2 className="notif-group">New</h2>
            <ul className="notif-list">{fresh.map((n) => <Row key={n.id} n={n} onFollowed={followed} />)}</ul>
          </>}
          {earlier.length > 0 && <>
            <h2 className="notif-group">Earlier</h2>
            <ul className="notif-list">{earlier.map((n) => <Row key={n.id} n={n} onFollowed={followed} />)}</ul>
          </>}
          {next && (
            <div className="load-more">
              <button className="btn btn-ghost btn-small" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Loading…' : 'Show older'}
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
