import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import { tagFor } from '../components/PostCard.jsx'
import { CloseIcon, PhotoIcon } from '../components/Icons.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { CATEGORIES, colorFor } from '../utils/categories.js'
import { ACCEPTED_TYPES, preparePostImage } from '../utils/image.js'

const MAX = 2000

export default function CreatePost() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef(null)
  const [image, setImage] = useState(null)     // prepared File, ready to upload
  const [preview, setPreview] = useState('')   // object URL for the preview
  const [preparing, setPreparing] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [content, setContent] = useState('')
  const [category, setCategory] = useState(user.interests?.[0] || '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  async function pick(file) {
    if (!file) return
    setError('')
    if (!ACCEPTED_TYPES.includes(file.type)) return setError('Please choose a JPEG, PNG, GIF or WebP image.')
    setPreparing(true)
    try {
      const ready = await preparePostImage(file)
      setImage(ready)
      setPreview(URL.createObjectURL(ready))
    } catch (e) {
      setError(e.message)
    } finally {
      setPreparing(false)
    }
  }

  async function submit(e) {
    e.preventDefault()
    if (!category) return setError('Pick a channel so the right people see your post.')
    if (!image && !content.trim()) return setError('Write something or attach a photo.')
    setBusy(true)
    setError('')
    try {
      await api.createPost(content, category, image)
      navigate(`/profile/${user.username}`)
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  const canPost = !busy && !preparing && (image || content.trim()) && category

  return (
    <div className="narrow">
      <h1 className="page-title">New post</h1>
      <form className={`card composer ${dragging ? 'dragging' : ''}`} onSubmit={submit}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setDragging(false) }}
        onDrop={(e) => { e.preventDefault(); setDragging(false); pick(e.dataTransfer.files?.[0]) }}>

        <div className="composer-main">
          <Avatar username={user.username} url={user.avatarUrl} size={40} />
          <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={image ? 3 : 6} maxLength={MAX} autoFocus
            aria-label={image ? 'Caption' : 'Post text'}
            placeholder={image ? 'Say something about this photo…' : 'What did you learn or build? A TIL, a bug you finally squashed, something you shipped…'} />
        </div>

        {preview && (
          <figure className="composer-photo">
            <img src={preview} alt="Preview of your photo" />
            <button type="button" className="remove-photo" onClick={() => { setImage(null); setPreview('') }} aria-label="Remove photo">
              <CloseIcon size={16} />
            </button>
          </figure>
        )}
        {dragging && <div className="drop-hint" aria-hidden="true">Drop the image to attach it</div>}

        <fieldset className="composer-channels">
          <legend>Channel</legend>
          <div className="tag-picker">
            {CATEGORIES.map((c) => (
              <button type="button" key={c} onClick={() => setCategory(c)} aria-pressed={category === c}
                className={category === c ? 'tag on' : 'tag'} style={{ '--cat': colorFor(c) }} title={c}>
                {tagFor(c)}
              </button>
            ))}
          </div>
        </fieldset>

        <div className="composer-bar">
          <input ref={fileRef} type="file" accept={ACCEPTED_TYPES.join(',')} hidden
            onChange={(e) => { pick(e.target.files?.[0]); e.target.value = '' }} />
          <button type="button" className="btn btn-ghost btn-small" onClick={() => fileRef.current?.click()} disabled={preparing}>
            <PhotoIcon size={17} /> {preparing ? 'Preparing…' : image ? 'Replace photo' : 'Add photo'}
          </button>
          <span className={`counter mono ${content.length > MAX - 100 ? 'warn' : ''}`}>{content.length}/{MAX}</span>
          <button type="button" className="btn btn-quiet" onClick={() => navigate(-1)}>Cancel</button>
          <button className="btn btn-accent" disabled={!canPost}>{busy ? 'Publishing…' : 'Publish'}</button>
        </div>
        {error && <p className="error composer-error" role="alert">{error}</p>}
      </form>
      <p className="muted small composer-tip">Tip: drag a screenshot anywhere onto the card. Photos are resized before upload.</p>
    </div>
  )
}
