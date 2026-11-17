import { useEffect, useRef } from 'react'

const SCRIPT = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let loading = null

function loadTurnstile() {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = SCRIPT
      s.async = true
      s.onload = () => resolve(window.turnstile)
      s.onerror = () => { loading = null; reject(new Error('Could not load the captcha. Check your connection.')) }
      document.head.appendChild(s)
    })
  }
  return loading
}

/**
 * Cloudflare Turnstile widget. Renders nothing unless the server provided a site key
 * (TURNSTILE_SITE_KEY). Calls onToken(token) when solved and onToken('') when it expires.
 * Change `resetKey` to get a fresh challenge (tokens are single-use, e.g. after a failed submit).
 */
export default function Captcha({ siteKey, onToken, resetKey = 0, onError }) {
  const box = useRef(null)
  const cb = useRef(onToken)
  cb.current = onToken

  useEffect(() => {
    if (!siteKey) return
    let id = null
    let cancelled = false
    cb.current('')
    loadTurnstile().then((t) => {
      if (cancelled || !box.current) return
      id = t.render(box.current, {
        sitekey: siteKey,
        callback: (token) => cb.current(token),
        'expired-callback': () => cb.current(''),
        'error-callback': () => cb.current(''),
      })
    }).catch((e) => onError?.(e.message))
    return () => {
      cancelled = true
      if (id !== null && window.turnstile) window.turnstile.remove(id)
    }
  }, [siteKey, resetKey]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!siteKey) return null
  return <div className="captcha" ref={box} />
}
