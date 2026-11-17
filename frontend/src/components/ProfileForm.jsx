import { useState } from 'react'
import AvatarPicker from './AvatarPicker.jsx'
import { tagFor } from './PostCard.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import { CATEGORIES, colorFor } from '../utils/categories.js'

/** Profile editor: photo (saved immediately), then bio, skills, GitHub and interests (saved on submit). */
export default function ProfileForm({ initial, submitLabel = 'Save changes', onSaved, extraAction }) {
  const { updateUser } = useAuth()
  // displayName falls back to the username in responses; only prefill a real, different name.
  const [displayName, setDisplayName] = useState(initial.displayName && initial.displayName !== initial.username ? initial.displayName : '')
  const [bio, setBio] = useState(initial.bio || '')
  const [githubUrl, setGithubUrl] = useState(initial.githubUrl || '')
  const [skills, setSkills] = useState((initial.skills || []).join(', '))
  const [interests, setInterests] = useState(initial.interests || [])
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  const toggleInterest = (c) => {
    setSaved(false)
    setInterests((prev) => (prev.includes(c) ? prev.filter((x) => x !== c) : [...prev, c]))
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    setSaved(false)
    try {
      const result = await api.updateMe({
        displayName, bio, githubUrl, interests,
        skills: skills.split(',').map((s) => s.trim()).filter(Boolean),
      })
      updateUser(result)
      setSaved(true)
      onSaved?.(result)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="form" onChange={() => setSaved(false)}>
      <section className="form-section">
        <h2>Profile photo</h2>
        <AvatarPicker size={72} />
      </section>

      <section className="form-section">
        <h2>About you</h2>
        <label className="field">
          <span>Name</span>
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50}
            placeholder={initial.username} autoComplete="name" />
          <span className="hint">Shown on your posts and profile. People can search for it. Leave empty to use @{initial.username}.</span>
        </label>
        <label className="field">
          <span>Bio</span>
          <textarea value={bio} onChange={(e) => setBio(e.target.value)} maxLength={500} rows={3}
            placeholder="What are you learning or building right now?" />
          <span className="hint">{bio.length} / 500</span>
        </label>
        <label className="field">
          <span>Skills</span>
          <input value={skills} onChange={(e) => setSkills(e.target.value)} placeholder="Java, Spring Boot, Linux" />
          <span className="hint">Comma-separated. People can find you by these.</span>
        </label>
        <label className="field">
          <span>GitHub</span>
          <input type="url" value={githubUrl} onChange={(e) => setGithubUrl(e.target.value)} placeholder="https://github.com/you" />
        </label>
      </section>

      <section className="form-section">
        <h2>Channels you follow</h2>
        <p className="hint">Posts tagged with these rank higher in “For you”.</p>
        <div className="tag-picker">
          {CATEGORIES.map((c) => (
            <button type="button" key={c} onClick={() => toggleInterest(c)} aria-pressed={interests.includes(c)}
              className={interests.includes(c) ? 'tag on' : 'tag'} style={{ '--cat': colorFor(c) }} title={c}>
              {tagFor(c)}
            </button>
          ))}
        </div>
      </section>

      <div className="form-actions">
        <button className="btn btn-accent" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
        {extraAction}
        {saved && <span className="saved" role="status">✓ Saved</span>}
        {error && <span className="error" role="alert">{error}</span>}
      </div>
    </form>
  )
}
