/* Push notifications for Techy, loaded into the service worker (see vite.config.js → workbox.importScripts).
 * The server sends small encrypted JSON payloads: { title, body, url, tag }. */

self.addEventListener('push', (event) => {
  let data = {}
  try { data = event.data ? event.data.json() : {} } catch { data = { title: 'Techy', body: event.data?.text() } }

  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    // Open tabs refresh their badges (unread messages, notifications) right away.
    windows.forEach((w) => w.postMessage({ type: 'techy:push', tag: data.tag }))
    // If Techy is open and in front, the app itself shows the news; don't also pop a notification.
    if (windows.some((w) => w.focused && w.visibilityState === 'visible')) return

    await self.registration.showNotification(data.title || 'Techy', {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/badge-96.png',
      tag: data.tag || undefined,
      renotify: Boolean(data.tag),
      data: { url: data.url || '/' },
    })
  })())
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const existing = windows.find((w) => new URL(w.url).origin === self.location.origin)
    if (existing) {
      await existing.focus()
      return existing.navigate ? existing.navigate(url).catch(() => existing.postMessage({ type: 'techy:open', url })) : null
    }
    return self.clients.openWindow(url)
  })())
})

// The browser replaced the subscription (keys rotated): tell an open tab to re-register it.
self.addEventListener('pushsubscriptionchange', (event) => {
  event.waitUntil(self.clients.matchAll({ type: 'window' })
    .then((ws) => ws.forEach((w) => w.postMessage({ type: 'techy:resubscribe' }))))
})
