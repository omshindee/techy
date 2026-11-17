import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import PostCard, { tagFor } from '../components/PostCard.jsx'
import Avatar from '../components/Avatar.jsx'
import UserCard from '../components/UserCard.jsx'
import Loading from '../components/Loading.jsx'
import { PhotoIcon, SearchIcon } from '../components/Icons.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { CATEGORIES, colorFor } from '../utils/categories.js'
import InstallBanner from '../components/InstallPrompt.jsx'

/** Feed switcher: ranked "For you", everything "Latest", or one #channel per category. */
function Channels({ value, onPick }) {
  return (
    <nav className="channels" aria-label="Feed">
      <div className="channels-main" role="tablist">
        {[['feed', 'For you'], ['latest', 'Latest']].map(([key, label]) => (
          <button key={key} role="tab" aria-selected={value === key} className={value === key ? 'on' : ''}
            onClick={() => onPick(key)}>{label}</button>
        ))}
      </div>
      <div className="channels-tags">
        {CATEGORIES.map((c) => (
          <button key={c} onClick={() => onPick(c)} aria-pressed={value === c} className={value === c ? 'tag on' : 'tag'}
            style={{ '--cat': colorFor(c) }} title={c}>
            {tagFor(c)}
          </button>
        ))}
      </div>
    </nav>
  )
}

/** "Start a post" box at the top of the feed. */
function ComposePrompt() {
  const { user } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="card compose-prompt">
      <Avatar username={user.username} url={user.avatarUrl} size={40} />
      <Link to="/new" className="compose-fake">What did you learn or build today?</Link>
      <button className="icon-button" onClick={() => navigate('/new')} aria-label="Post a photo" title="Post a photo">
        <PhotoIcon size={22} />
      </button>
    </div>
  )
}

/** Right column on wide screens: search, people to follow, your interests. */
function Rail() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [people, setPeople] = useState(null)
  const [q, setQ] = useState('')

  useEffect(() => {
    let cancelled = false
    api.suggestions(4)
      .then((list) => !cancelled && setPeople(list))
      .catch(() => !cancelled && setPeople([]))
    return () => { cancelled = true }
  }, [user.id])

  return (
    <aside className="rail">
      <form className="search-box" role="search"
        onSubmit={(e) => { e.preventDefault(); navigate(`/people${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`) }}>
        <SearchIcon size={16} />
        <input type="search" enterKeyHint="search" autoCapitalize="none" autoCorrect="off" value={q} onChange={(e) => setQ(e.target.value)}
          placeholder="Search names, @usernames, skills" aria-label="Search people" />
      </form>

      {people?.length > 0 && (
        <section className="card rail-card">
          <h2>Developers to follow</h2>
          {people.map((u) => <UserCard key={u.id} user={u} compact />)}
          <Link to="/people" className="rail-more">Browse everyone →</Link>
        </section>
      )}

      <section className="card rail-card">
        <h2>Your channels</h2>
        {user.interests?.length ? (
          <div className="rail-tags">
            {user.interests.map((c) => (
              <Link key={c} to={`/?c=${encodeURIComponent(c)}`} className="tag" style={{ '--cat': colorFor(c) }}>{tagFor(c)}</Link>
            ))}
          </div>
        ) : (
          <p className="muted small">Pick interests in <Link to="/settings">your profile</Link> to tune “For you”.</p>
        )}
      </section>
      <p className="rail-foot">Techy · a network for people who build things</p>
    </aside>
  )
}

export default function Home() {
  const [params, setParams] = useSearchParams()
  const view = params.get('c') || 'feed' // 'feed' | 'latest' | category
  const [posts, setPosts] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setPosts(null)
    setError('')
    const load = view === 'feed' ? api.feed() : api.posts({ category: view === 'latest' ? '' : view })
    load.then((p) => !cancelled && setPosts(p)).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [view])

  const pick = (key) => {
    setParams(key === 'feed' ? {} : { c: key })
    window.scrollTo({ top: 0 })
  }

  return (
    <div className="home">
      <div className="feed-column">
        <h1 className="page-title">
          {view === 'feed' ? 'Home' : view === 'latest' ? 'Latest' : <span style={{ color: colorFor(view) }}>{tagFor(view)}</span>}
        </h1>
        <InstallBanner />
        <Channels value={view} onPick={pick} />
        <ComposePrompt />
        {error && <p className="error">{error}</p>}
        {!posts && !error && <Loading label="Loading posts" />}
        {posts?.length === 0 && (
          <div className="card empty">
            <h2>{view === 'feed' || view === 'latest' ? 'Nothing here yet' : `No ${view} posts yet`}</h2>
            <p className="muted">
              <Link to="/people">Follow a few developers</Link> or <Link to="/new">write the first post</Link>.
            </p>
          </div>
        )}
        <div className="feed">
          {posts?.map((p) => (
            <PostCard key={p.id} post={p} onDelete={(id) => setPosts((prev) => prev.filter((x) => x.id !== id))}
              onAuthorBlocked={(authorId) => setPosts((prev) => prev.filter((x) => x.author.id !== authorId))} />
          ))}
        </div>
      </div>
      <Rail />
    </div>
  )
}
