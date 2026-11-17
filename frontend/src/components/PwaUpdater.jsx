import { useEffect } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'

const CHECK_EVERY_MS = 60 * 60 * 1000 // also checked whenever the app comes back to the foreground

/**
 * Registers the service worker and offers new versions.
 * A new version waits until the user taps "Update" (so nobody loses a half-written post), then the
 * page reloads onto it. Old caches are cleaned up by Workbox, so nobody gets stuck on an old build.
 * We deliberately don't announce "ready to work offline": Techy needs the server for everything.
 */
export default function PwaUpdater() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      if (!registration) return
      const check = () => { if (navigator.onLine) registration.update().catch(() => {}) }
      setInterval(check, CHECK_EVERY_MS)
      document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check() })
    },
    onRegisterError(error) {
      console.warn('Service worker registration failed', error)
    },
  })

  // Expose for automated checks and debugging (no user data involved).
  useEffect(() => { window.__techyUpdateReady = needRefresh }, [needRefresh])

  if (!needRefresh) return null
  return (
    <div className="toast" role="status">
      <span>A new version of Techy is available.</span>
      <span className="toast-actions">
        <button className="btn btn-small btn-quiet toast-dismiss" onClick={() => setNeedRefresh(false)}>Later</button>
        <button className="btn btn-small btn-accent" onClick={() => updateServiceWorker(true)}>Update</button>
      </span>
    </div>
  )
}
