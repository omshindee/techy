import { useRef, useState } from 'react'
import Avatar from './Avatar.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { prepareAvatar } from '../utils/image.js'

/**
 * Upload / replace / remove your profile photo. The browser crops it to a square, the server stores it.
 * layout="avatar": the photo itself is the button (profile header), with a small camera badge.
 * layout="row": photo plus "Upload new photo" / "Remove" buttons (settings, onboarding).
 */
export default function AvatarPicker({ size = 72, layout = 'row', onChange }) {
  const { user, updateUser } = useAuth()
  const fileRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const choose = () => fileRef.current?.click()

  async function apply(promise) {
    setBusy(true)
    setError('')
    try {
      const saved = await promise
      updateUser(saved)
      onChange?.(saved)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function onFile(e) {
    const file = e.target.files?.[0]
    e.target.value = '' // allow picking the same file again
    if (!file) return
    await apply(prepareAvatar(file).then((img) => api.uploadAvatar(img)))
  }

  const photo = (
    <span className={`avatar-picker-photo ${busy ? 'busy' : ''}`}>
      <Avatar username={user.username} url={user.avatarUrl} size={size} />
      {busy && <span className="spinner on-photo" aria-hidden="true" />}
    </span>
  )

  return (
    <div className={`avatar-picker avatar-picker--${layout}`}>
      <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={onFile} />
      {layout === 'avatar' ? (
        <button type="button" className="avatar-button" onClick={choose} disabled={busy}
          aria-label={user.avatarUrl ? 'Change profile photo' : 'Add a profile photo'}
          title={user.avatarUrl ? 'Change profile photo' : 'Add a profile photo'}>
          {photo}
          <span className="avatar-badge" aria-hidden="true">＋</span>
        </button>
      ) : (
        <>
          {photo}
          <div className="avatar-picker-actions">
            <button type="button" className="btn btn-ghost btn-small" onClick={choose} disabled={busy}>
              {busy ? 'Uploading…' : user.avatarUrl ? 'Upload new photo' : 'Upload a photo'}
            </button>
            {user.avatarUrl && (
              <button type="button" className="btn btn-quiet btn-small" onClick={() => apply(api.removeAvatar())} disabled={busy}>
                Remove
              </button>
            )}
            <span className="muted small">JPEG, PNG, GIF or WebP. We crop it to a square.</span>
          </div>
        </>
      )}
      {error && <p className="error small" role="alert">{error}</p>}
    </div>
  )
}
