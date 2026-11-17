import { api } from './api.js'
import { getToken } from '../utils/auth.js'

/**
 * Phone/desktop notifications while Techy is closed (Web Push).
 *
 * States: 'unsupported' (this browser can't), 'ios-install' (iPhone/iPad: works only from the Home
 * Screen app), 'unavailable' (server has no keys, or no service worker, e.g. dev mode),
 * 'denied' (blocked in browser settings), 'off', 'on'.
 */

const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
const isStandalone = () => window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true

let keyPromise
const serverKey = () => (keyPromise ??= api.pushConfig().then((c) => c.publicKey).catch(() => { keyPromise = null; return null }))

async function registration() {
  if (!('serviceWorker' in navigator)) return null
  return (await navigator.serviceWorker.getRegistration()) ?? null
}

export async function pushState() {
  const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
  if (!supported) return isIos() && !isStandalone() ? 'ios-install' : 'unsupported'
  if (Notification.permission === 'denied') return 'denied'
  const [reg, key] = await Promise.all([registration(), serverKey()])
  if (!key) return 'no-server-key'
  if (!reg) return 'unavailable'
  const sub = await reg.pushManager.getSubscription()
  return sub && Notification.permission === 'granted' ? 'on' : 'off'
}

function toBytes(base64url) {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4)
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

/** Asks for permission (must run from a tap) and registers this device. Returns the new state. */
export async function enablePush() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission === 'denied' ? 'denied' : 'off'
  const [reg, key] = await Promise.all([registration(), serverKey()])
  if (!reg || !key) return 'unavailable'
  let sub = await reg.pushManager.getSubscription()
  // A subscription made with another server key can't be reused.
  const existingKey = sub?.options?.applicationServerKey
  if (sub && existingKey && !sameKey(new Uint8Array(existingKey), toBytes(key))) {
    await sub.unsubscribe().catch(() => {})
    sub = null
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toBytes(key) })
  await api.pushSubscribe(sub.toJSON())
  return 'on'
}

export async function disablePush() {
  const reg = await registration()
  const sub = await reg?.pushManager.getSubscription()
  if (sub) {
    await api.pushUnsubscribe(sub.endpoint).catch(() => {})
    await sub.unsubscribe().catch(() => {})
  }
  return 'off'
}

/** On sign-out: stop this device getting the old account's notifications. Never blocks sign-out. */
export function forgetDeviceOnSignOut() {
  const token = getToken() // read now; sign-out clears it right after
  if (!token || !('serviceWorker' in navigator)) return
  registration().then((reg) => reg?.pushManager?.getSubscription()).then((sub) => {
    if (!sub) return
    fetch('/api/push/unsubscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ endpoint: sub.endpoint }),
      keepalive: true,
    }).catch(() => {})
    sub.unsubscribe().catch(() => {})
  }).catch(() => {})
}

/** Re-sends this device's subscription after sign-in or when the browser rotated it. */
export async function refreshSubscription() {
  try {
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    const reg = await registration()
    const sub = await reg?.pushManager?.getSubscription()
    if (sub && await serverKey()) await api.pushSubscribe(sub.toJSON())
  } catch { /* best effort */ }
}

function sameKey(a, b) {
  return a.length === b.length && a.every((x, i) => x === b[i])
}
