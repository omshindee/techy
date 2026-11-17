import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Avatar from './Avatar.jsx'
import CommentSection from './CommentSection.jsx'
import Modal, { ActionSheet } from './Modal.jsx'
import ReportDialog from './ReportDialog.jsx'
import { CommentIcon, HeartIcon, MoreIcon } from './Icons.jsx'
import { api, mediaUrl } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { colorFor, categoriesOf } from '../utils/categories.js'
import { count, timeAgo } from '../utils/format.js'

const LONG_TEXT = 480 // characters before "Show more"

const SLUGS = { 'C++': 'cpp', 'Operating Systems': 'os', 'Web Development': 'webdev', 'AI/ML': 'ai-ml' }

/** Category as a code-style tag: "Web Development" -> "#webdev". */
export const tagFor = (category) => `#${SLUGS[category] || category.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`

/**
 * One post: author line, text, optional photo, then reaction pills and a threaded comment section.
 * `onChange(post)` reports like/comment changes so lists can stay in sync; `onAuthorBlocked(authorId)`
 * lets the list drop everything by someone you just blocked.
 */
export default function PostCard({ post: initial, onDelete, onChange, onAuthorBlocked, startWithComments = false }) {
  const { user } = useAuth()
  const [post, setPost] = useState(initial)
  const [error, setError] = useState('')
  const [liking, setLiking] = useState(false)
  const likingRef = useRef(false)
  const [menu, setMenu] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [zoom, setZoom] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [showComments, setShowComments] = useState(startWithComments)
  const cats = categoriesOf(post)
  const color = colorFor(cats[0])
  const own = user.id === post.author.id

  const first = useRef(true)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  useEffect(() => {
    if (first.current) { first.current = false; return }
    onChangeRef.current?.(post)
  }, [post])

  async function toggleLike() {
    // Ref, not state: state isn't updated yet for clicks that land in the same tick.
    if (likingRef.current) return
    likingRef.current = true
    setLiking(true)
    setError('')
    const target = !post.likedByMe
    // Optimistic update, rolled back on failure.
    setPost((p) => ({ ...p, likedByMe: target, likeCount: p.likeCount + (target ? 1 : -1) }))
    try {
      const res = target ? await api.like(post.id) : await api.unlike(post.id)
      setPost((p) => ({ ...p, likedByMe: res.liked, likeCount: res.likeCount }))
    } catch (e) {
      setPost((p) => ({ ...p, likedByMe: !target, likeCount: p.likeCount + (target ? -1 : 1) }))
      setError(e.message)
    } finally {
      likingRef.current = false
      setLiking(false)
    }
  }

  async function block() {
    const who = `@${post.author.username}`
    if (!window.confirm(`Block ${who}? You won’t see each other’s posts, comments or profiles, and neither of you can follow the other. They aren’t notified.`)) return
    try {
      await api.block(post.author.username)
      onAuthorBlocked?.(post.author.id)
    } catch (e) {
      setError(e.message)
    }
  }

  async function remove() {
    if (!window.confirm(own ? 'Delete this post? This can’t be undone.' : 'Remove this post as a moderator? This can’t be undone.')) return
    try {
      await api.deletePost(post.id)
      onDelete?.(post.id)
    } catch (e) {
      setError(e.message)
    }
  }

  const longText = post.content.length > LONG_TEXT && !expanded
  const text = longText ? `${post.content.slice(0, LONG_TEXT).trimEnd()}…` : post.content
  const profile = `/profile/${post.author.username}`

  return (
    <article className="post" style={{ '--cat': color }}>
      <header className="post-head">
        <Link to={profile} tabIndex={-1}><Avatar username={post.author.username} url={post.author.avatarUrl} size={40} /></Link>
        <div className="post-meta">
          <Link to={profile} className="username">{post.author.displayName || post.author.username}</Link>
          <span className="handle">@{post.author.username}</span>
          <time dateTime={post.createdAt} title={new Date(post.createdAt).toLocaleString()}>{timeAgo(post.createdAt)}</time>
        </div>
        <span className="post-tags">
          {cats.map((c) => (
            <Link key={c} to={`/?c=${encodeURIComponent(c)}`} className="tag" style={{ '--cat': colorFor(c) }} title={`More ${c} posts`}>
              {tagFor(c)}
            </Link>
          ))}
        </span>
        <button className="icon-button" onClick={() => setMenu(true)} aria-label="Post options"><MoreIcon size={20} /></button>
      </header>

      {post.content && (
        <div className="post-text">
          <p>{text}</p>
          {longText && <button className="link-button" onClick={() => setExpanded(true)}>Show more</button>}
        </div>
      )}

      {post.imageUrl && (
        <button className="post-photo" onClick={() => setZoom(true)} aria-label="View photo full size">
          <img src={mediaUrl(post.imageUrl)} loading="lazy"
            alt={post.content ? `Photo: ${post.content.slice(0, 120)}` : `Photo by ${post.author.username}`} />
        </button>
      )}

      <footer className="post-actions">
        <button className={`pill ${post.likedByMe ? 'on' : ''}`} onClick={toggleLike}
          aria-pressed={post.likedByMe} aria-busy={liking} aria-label={`${post.likedByMe ? 'Unlike' : 'Like'} (${post.likeCount})`}>
          <HeartIcon filled={post.likedByMe} size={17} /><span>{count(post.likeCount)}</span>
        </button>
        <button className={`pill ${showComments ? 'active' : ''}`} onClick={() => setShowComments((s) => !s)}
          aria-expanded={showComments} aria-label={`Comments (${post.commentCount})`}>
          <CommentIcon size={17} /><span>{count(post.commentCount)}</span>
        </button>
      </footer>
      {error && <p className="error small post-error">{error}</p>}

      {showComments && (
        <CommentSection postId={post.id} postAuthorId={post.author.id}
          onAdded={() => setPost((p) => ({ ...p, commentCount: p.commentCount + 1 }))}
          onRemoved={() => setPost((p) => ({ ...p, commentCount: Math.max(0, p.commentCount - 1) }))} />
      )}

      {menu && (
        <ActionSheet onClose={() => setMenu(false)} options={own
          ? [{ label: 'Delete post', danger: true, onClick: remove }]
          : [
              { label: 'Report post', danger: true, onClick: () => setReporting(true) },
              { label: `Block @${post.author.username}`, onClick: block },
              ...(user.admin ? [{ label: 'Remove post (moderator)', danger: true, onClick: remove }] : []),
            ]} />
      )}
      {reporting && <ReportDialog targetType="POST" targetId={post.id} onClose={() => setReporting(false)} />}
      {zoom && (
        <Modal title="Photo" onClose={() => setZoom(false)} className="lightbox">
          <img src={mediaUrl(post.imageUrl)} alt="" />
        </Modal>
      )}
    </article>
  )
}
