import { useState } from 'react'
import { Link } from 'react-router-dom'
import Avatar from './Avatar.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

/** Follow / Following toggle. `onChange(user)` receives the updated user. */
export function FollowButton({ user, onChange, small = false }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function toggle() {
    setBusy(true)
    setError('')
    try {
      onChange(user.followedByMe ? await api.unfollow(user.username) : await api.follow(user.username))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="follow">
      <button className={`btn ${user.followedByMe ? 'btn-ghost' : 'btn-accent'} ${small ? 'btn-small' : ''}`}
        onClick={toggle} disabled={busy} aria-pressed={user.followedByMe}>
        {user.followedByMe ? 'Following' : 'Follow'}
      </button>
      {error && <span className="error small" role="alert">{error}</span>}
    </span>
  )
}

/** A person in a list: photo, name, skills, follower count, follow button. */
export default function UserCard({ user: initial, compact = false, onNavigate }) {
  const { user: me } = useAuth()
  const [user, setUser] = useState(initial)
  const profile = `/profile/${user.username}`

  return (
    <div className={`person ${compact ? 'compact' : ''}`}>
      <Link to={profile} onClick={onNavigate} tabIndex={-1}>
        <Avatar username={user.username} url={user.avatarUrl} size={compact ? 36 : 48} />
      </Link>
      <div className="person-text">
        <span className="person-name">
          <Link to={profile} onClick={onNavigate} className="username">{user.displayName || user.username}</Link>
          <span className="handle">@{user.username}</span>
        </span>
        {compact ? (
          <span className="muted small ellipsis">{user.skills.slice(0, 3).join(' · ') || `${user.followerCount} followers`}</span>
        ) : (
          <>
            {user.bio && <span className="person-bio">{user.bio}</span>}
            <span className="person-meta">
              {user.skills.slice(0, 4).map((s) => <span key={s} className="chip">{s}</span>)}
              <span className="mono muted small">{user.followerCount} followers · {user.postCount} posts</span>
            </span>
          </>
        )}
      </div>
      {me.id !== user.id && !user.blockedByMe && <FollowButton user={user} onChange={setUser} small={compact} />}
    </div>
  )
}
