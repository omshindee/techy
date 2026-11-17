// Install ("Add to Home Screen") support, shared by the install banner and the Settings card.
import { useEffect, useState } from 'react'

const DISMISS_KEY = 'techy.installBanner.dismissedAt'
const DISMISS_FOR_MS = 30 * 24 * 60 * 60 * 1000 // a dismissed banner stays away for 30 days

// Chrome/Edge/Samsung fire `beforeinstallprompt` early, sometimes before React mounts, so capture it
// at module load (imported from main.jsx) and keep it until the user asks to install.
let deferredPrompt = null
const listeners = new Set()
const notify = () => listeners.forEach((fn) => fn())

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault() // we show our own button instead of the browser's mini-infobar
    deferredPrompt = e
    notify()
  })
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
    notify()
  })
}

/** Running as an installed app (home-screen launch, no browser UI). */
export function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true
}

/** iPhone/iPad: no install prompt API, installation is Share → Add to Home Screen. */
export function isIOS() {
  const ua = navigator.userAgent
  return /iphone|ipad|ipod/i.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

export function useInstall() {
  const [, rerender] = useState(0)
  const [installed, setInstalled] = useState(isStandalone())

  useEffect(() => {
    const update = () => rerender((n) => n + 1)
    const onInstalled = () => setInstalled(true)
    listeners.add(update)
    window.addEventListener('appinstalled', onInstalled)
    const mq = window.matchMedia?.('(display-mode: standalone)')
    const onMode = () => setInstalled(isStandalone())
    mq?.addEventListener?.('change', onMode)
    return () => {
      listeners.delete(update)
      window.removeEventListener('appinstalled', onInstalled)
      mq?.removeEventListener?.('change', onMode)
    }
  }, [])

  async function promptInstall() {
    if (!deferredPrompt) return 'unavailable'
    const e = deferredPrompt
    deferredPrompt = null // the browser allows each prompt to be used once
    e.prompt()
    const { outcome } = await e.userChoice
    notify()
    if (outcome === 'accepted') setInstalled(true)
    return outcome
  }

  return {
    installed,
    canPrompt: !installed && deferredPrompt !== null,
    ios: !installed && isIOS(),
    promptInstall,
  }
}

export function bannerDismissed() {
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY))
    return at > 0 && Date.now() - at < DISMISS_FOR_MS
  } catch {
    return false
  }
}

export function dismissBanner() {
  try { localStorage.setItem(DISMISS_KEY, String(Date.now())) } catch { /* private mode: just hide for now */ }
}
