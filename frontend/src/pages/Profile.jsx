import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import AvatarPicker from '../components/AvatarPicker.jsx'
import Loading from '../components/Loading.jsx'
import PostCard, { tagFor } from '../components/PostCard.jsx'
import PostModal from '../components/PostModal.jsx'
import UserListModal from '../components/UserListModal.jsx'
import ReportDialog from '../components/ReportDialog.jsx'
import { ActionSheet } from '../components/Modal.jsx'
import { FollowButton } from '../components/UserCard.jsx'
import { ChatIcon, GithubIcon, ListIcon, MoreIcon, PenIcon, PhotoIcon } from '../components/Icons.jsx'
import { api, mediaUrl } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { colorFor } from '../utils/categories.js'
import { count } from '../utils/format.js'

/** Cover strip painted from the person's interest colors on a faint graph-paper grid. */
function coverStyle(interests) {
  const colors = interests.length ? interests.map(colorFor) : ['#ff5a1f', '#ffb020']
  const stops = colors.length === 1 ? [colors[0], colors[0]] : colors
  return { '--cover': `linear-gradient(120deg, ${stops.join(', ')})` }
}

export default function Profile() {
  const { username } = useParams()
  const { user: me } = useAuth()
  const [profile, setProfile] = useState(null)
  const [posts, setPosts] = useState(null)
  const [error, setError] = useState('')
  const [tab, setTab] = useState('posts')
  const [openPost, setOpenPost] = useState(null)
  const [list, setList] = useState(null) // 'followers' | 'following'
  const [menu, setMenu] = useState(false)
  const [reporting, setReporting] = useState(false)

  useEffect(() => {
    let cancelled = false
    setProfile(null); setPosts(null); setError(''); setOpenPost(null); setList(null); setTab('posts')
    api.user(username).then((p) => !cancelled && setProfile(p)).catch((e) => !cancelled && setError(e.message))
    api.posts({ username }).then((p) => !cancelled && setPosts(p)).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [username])

  if (error && !profile) return <div className="card empty"><h2>Profile not found</h2><p className="muted">{error}</p></div>
  if (!profile) return <Loading />
  const own = me.id === profile.id
  // Your own profile reads photo/bio from the auth context, so edits show up instantly.
  const shown = own
    ? { ...profile, displayName: me.displayName, avatarUrl: me.avatarUrl, bio: me.bio, skills: me.skills, interests: me.interests, githubUrl: me.githubUrl }
    : profile
  const handle = `@${profile.username}`

  async function act(fn, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return
    try {
      const updated = await fn()
      setProfile(updated)
      return updated
    } catch (e) {
      setError(e.message)
    }
  }
  const block = () => act(() => api.block(profile.username),
    `Block ${handle}? You won’t see each other’s posts, comments or profiles, and neither of you can follow the other. They aren’t notified.`)
    .then((u) => { if (u) setPosts([]) })
  const unblock = () => act(() => api.unblock(profile.username))
    .then((u) => { if (u) api.posts({ username }).then(setPosts).catch(() => {}) })
  const menuOptions = [
    { label: `Report ${handle}`, danger: true, onClick: () => setReporting(true) },
    profile.blockedByMe ? { label: `Unblock ${handle}`, onClick: unblock } : { label: `Block ${handle}`, onClick: block },
    ...(me.admin ? [profile.suspended
      ? { label: 'Lift suspension (moderator)', onClick: () => act(() => api.unsuspend(profile.username)) }
      : { label: 'Suspend account (moderator)', danger: true, onClick: () => act(() => api.suspend(profile.username),
          `Suspend ${handle}? They’ll be signed out, can’t sign in, and their profile and posts are hidden.`) }] : []),
  ]
  const photos = posts?.filter((p) => p.imageUrl) ?? []

  const updatePost = (p) => setPosts((prev) => prev.map((x) => (x.id === p.id ? p : x)))
  const removePost = (id) => {
    setPosts((prev) => prev.filter((x) => x.id !== id))
    setProfile((p) => ({ ...p, postCount: p.postCount - 1 }))
  }

  return (
    <div className="profile-page">
      <section className="card profile-card">
        <div className="cover" style={coverStyle(shown.interests)} />
        <div className="profile-top">
          <div className="profile-avatar">
            {own
              ? <AvatarPicker layout="avatar" size={104} onChange={(u) =>
                  setPosts((prev) => prev?.map((p) => ({ ...p, author: { ...p.author, avatarUrl: u.avatarUrl } })))} />
              : <Avatar username={shown.username} url={shown.avatarUrl} size={104} />}
          </div>
          <div className="profile-buttons">
            {own
              ? <Link to="/settings" className="btn btn-ghost"><PenIcon size={16} /> Edit profile</Link>
              : (
                <>
                  {profile.blockedByMe
                    ? <button className="btn btn-ghost" onClick={unblock}>Unblock</button>
                    : <FollowButton user={profile} onChange={setProfile} />}
                  {/* You can message people you follow (they can always reply from their inbox). */}
                  {profile.followedByMe && !profile.blockedByMe && (
                    <Link to={`/messages/${profile.username}`} className="btn btn-ghost"><ChatIcon size={16} /> Message</Link>
                  )}
                  <button className="icon-button bordered" onClick={() => setMenu(true)} aria-label="More options"><MoreIcon size={20} /></button>
                </>
              )}
          </div>
        </div>

        <div className="profile-about">
          <div className="profile-name">
            <h1>{shown.displayName || shown.username}</h1>
            <span className="handle">{handle}</span>
            {profile.suspended && <span className="badge danger">Suspended</span>}
          </div>
          {error && <p className="error" role="alert">{error}</p>}
          {shown.bio ? <p className="profile-bio">{shown.bio}</p>
            : own && <p className="muted">Add a bio so people know what you work on. <Link to="/settings">Edit profile</Link></p>}
          {shown.skills.length > 0 && (
            <ul className="chips" aria-label="Skills">{shown.skills.map((s) => <li key={s} className="chip">{s}</li>)}</ul>
          )}
          <div className="profile-links">
            {shown.interests.map((c) => (
              <Link key={c} to={`/?c=${encodeURIComponent(c)}`} className="tag" style={{ '--cat': colorFor(c) }}>{tagFor(c)}</Link>
            ))}
            {shown.githubUrl && (
              <a className="profile-gh" href={shown.githubUrl} target="_blank" rel="noopener noreferrer">
                <GithubIcon size={15} /> {shown.githubUrl.replace(/^https?:\/\/(www\.)?/, '')}
              </a>
            )}
          </div>
          <dl className="stats">
            <div><dt>posts</dt><dd>{count(profile.postCount)}</dd></div>
            <button onClick={() => setList('followers')}><dt>followers</dt><dd>{count(profile.followerCount)}</dd></button>
            <button onClick={() => setList('following')}><dt>following</dt><dd>{count(profile.followingCount)}</dd></button>
          </dl>
        </div>

        <div className="profile-tabs" role="tablist">
          <button role="tab" aria-selected={tab === 'posts'} className={tab === 'posts' ? 'on' : ''} onClick={() => setTab('posts')}>
            <ListIcon size={16} /> Posts
          </button>
          <button role="tab" aria-selected={tab === 'photos'} className={tab === 'photos' ? 'on' : ''} onClick={() => setTab('photos')}>
            <PhotoIcon size={16} /> Photos {posts && <span className="mono muted">{photos.length}</span>}
          </button>
        </div>
      </section>

      {profile.blockedByMe && (
        <div className="card empty">
          <h2>You blocked {handle}</h2>
          <p className="muted">You won’t see their posts or comments, and they can’t see yours or follow you.</p>
          <button className="btn btn-ghost" onClick={unblock}>Unblock</button>
        </div>
      )}

      {!profile.blockedByMe && !posts && <Loading />}

      {!profile.blockedByMe && posts && tab === 'posts' && (
        posts.length === 0 ? (
          <div className="card empty">
            <h2>{own ? 'You haven’t posted yet' : 'No posts yet'}</h2>
            {own && <p className="muted">Share a screenshot of something you built or a note on what you learned. <Link to="/new">Write a post</Link></p>}
          </div>
        ) : (
          <div className="feed">
            {posts.map((p) => <PostCard key={p.id} post={p} onChange={updatePost} onDelete={removePost} />)}
          </div>
        )
      )}

      {!profile.blockedByMe && posts && tab === 'photos' && (
        photos.length === 0 ? (
          <div className="card empty"><h2>No photos yet</h2></div>
        ) : (
          <div className="gallery">
            {photos.map((p) => (
              <button key={p.id} className="gallery-item" onClick={() => setOpenPost(p)} aria-label={`Open post: ${p.content.slice(0, 60) || 'photo'}`}>
                <img src={mediaUrl(p.imageUrl)} alt="" loading="lazy" />
                <span className="gallery-tag" style={{ '--cat': colorFor(p.category) }}>{tagFor(p.category)}</span>
              </button>
            ))}
          </div>
        )
      )}

      {openPost && <PostModal post={openPost} onClose={() => setOpenPost(null)} onChange={updatePost} onDelete={removePost} />}
      {list && <UserListModal username={profile.username} kind={list} onClose={() => setList(null)} />}
      {menu && <ActionSheet onClose={() => setMenu(false)} options={menuOptions} />}
      {reporting && <ReportDialog targetType="USER" targetId={profile.id} onClose={() => setReporting(false)} />}
    </div>
  )
}
