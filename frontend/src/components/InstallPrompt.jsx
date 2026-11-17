import { useState } from 'react'
import { CloseIcon } from './Icons.jsx'
import { bannerDismissed, dismissBanner, useInstall } from '../utils/install.js'

/** iOS share-sheet glyph (square with an up arrow), so the instructions match what people see. */
const ShareGlyph = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="share-glyph">
    <path d="M12 3v12M8 7l4-4 4 4" /><path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
  </svg>
)

export function IosSteps() {
  return (
    <ol className="ios-steps">
      <li>Tap <strong>Share</strong> <ShareGlyph /> in Safari’s toolbar.</li>
      <li>Scroll down and tap <strong>Add to Home Screen</strong>.</li>
      <li>Tap <strong>Add</strong>. Techy now opens from your home screen, full screen.</li>
    </ol>
  )
}

/**
 * Small, dismissible "Install Techy" banner on phones. Only shown when installing is actually
 * possible (an install prompt is available, or it's iOS), never inside the installed app,
 * and not again for 30 days after "Not now".
 */
export default function InstallBanner() {
  const { installed, canPrompt, ios, promptInstall } = useInstall()
  const [hidden, setHidden] = useState(bannerDismissed())
  const [showSteps, setShowSteps] = useState(false)

  if (installed || hidden || (!canPrompt && !ios)) return null
  const close = () => { dismissBanner(); setHidden(true) }

  return (
    <aside className="install-banner" aria-label="Install Techy">
      <img src="/icons/icon-192.png" alt="" width="40" height="40" />
      <div className="install-text">
        <strong>Install Techy</strong>
        <span>Open it from your home screen, full screen, like an app.</span>
        {showSteps && <IosSteps />}
      </div>
      <div className="install-actions">
        {canPrompt
          ? <button className="btn btn-accent btn-small" onClick={async () => { if ((await promptInstall()) !== 'accepted') close() }}>Install</button>
          : !showSteps && <button className="btn btn-accent btn-small" onClick={() => setShowSteps(true)}>How?</button>}
        <button className="icon-button" onClick={close} aria-label="Not now"><CloseIcon size={18} /></button>
      </div>
    </aside>
  )
}

/** Install section for Settings: always explains the current state, never nags. */
export function InstallCard() {
  const { installed, canPrompt, ios, promptInstall } = useInstall()
  const [result, setResult] = useState('')

  return (
    <section className="form-section">
      <h2>Techy app</h2>
      {installed ? (
        <p><span className="badge ok">✓ Installed</span> You’re using the installed app.</p>
      ) : canPrompt ? (
        <>
          <p className="muted">Add Techy to your home screen or desktop. It opens in its own window without browser bars.</p>
          <div className="row-actions">
            <button className="btn btn-accent" onClick={async () => {
              const outcome = await promptInstall()
              setResult(outcome === 'accepted' ? 'Installed. Look for Techy on your home screen.' : 'No problem. You can install it any time from here.')
            }}>Install Techy</button>
          </div>
          {result && <p className="small muted">{result}</p>}
        </>
      ) : ios ? (
        <>
          <p className="muted">Add Techy to your home screen to open it full screen, like an app:</p>
          <IosSteps />
        </>
      ) : (
        <p className="muted">
          To install Techy, open your browser’s menu and choose <strong>Install app</strong> or <strong>Add to Home screen</strong>.
          (Supported in Chrome, Edge and Samsung Internet; on iPhone use Safari’s Share menu.)
        </p>
      )}
    </section>
  )
}
