import { useEffect, useState } from 'react'

/**
 * The installed app opens from its cached shell even without a connection, so say plainly that
 * nothing can load or be posted until the connection is back (instead of pretending to work).
 */
export default function OfflineBanner() {
  const [offline, setOffline] = useState(typeof navigator !== 'undefined' && navigator.onLine === false)

  useEffect(() => {
    const on = () => setOffline(false)
    const off = () => setOffline(true)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off) }
  }, [])

  if (!offline) return null
  return (
    <div className="offline-banner" role="alert">
      You’re offline. Techy needs a connection to load posts or send anything; it will work again once you’re back online.
    </div>
  )
}
