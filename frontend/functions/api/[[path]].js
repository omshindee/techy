// Cloudflare Pages Function: forwards every /api/* request to the Spring Boot backend.
//
// Why: the browser only ever talks to your Pages domain, so there are no cross-origin requests,
// the relative "/api" URLs in the app (and the service worker rules) keep working unchanged, and
// the backend learns each visitor's real IP (needed for its rate limits).
//
// Configure in Cloudflare → Pages → your project → Settings → Variables and Secrets:
//   BACKEND_URL   (plain text) e.g. https://techy-api.onrender.com   (no trailing slash)
//   PROXY_SECRET  (secret)     the same long random value as the backend's PROXY_SECRET

const HOP_BY_HOP = ['host', 'connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'proxy-connection',
  'te', 'trailer', 'x-real-ip', 'x-forwarded-for', 'x-forwarded-host', 'x-forwarded-proto', 'x-proxy-secret',
  // The browser→Pages request is same-origin, so the backend doesn't need to run CORS checks (and
  // you don't have to list every Pages/preview domain in CORS_ALLOWED_ORIGINS). Still safe: logins
  // use bearer tokens that browsers never attach automatically, and without an Origin the backend
  // adds no Access-Control-Allow-Origin header, so other websites still can't read API responses.
  'origin']

export async function onRequest({ request, env }) {
  if (!env.BACKEND_URL) {
    // List variable NAMES only (never values) to make dashboard typos easy to spot.
    const visible = Object.keys(env || {}).filter((k) => k !== 'ASSETS').map((k) => JSON.stringify(k)).join(', ') || 'none'
    return Response.json({
      error: 'The API is not configured (BACKEND_URL is missing).',
      variablesVisibleToFunction: visible,
    }, { status: 503 })
  }

  const incoming = new URL(request.url)
  const target = new URL(incoming.pathname + incoming.search, env.BACKEND_URL)

  const headers = new Headers(request.headers)
  for (const h of HOP_BY_HOP) headers.delete(h) // never forward client-supplied proxy headers
  const ip = request.headers.get('CF-Connecting-IP')
  if (ip) {
    headers.set('X-Real-IP', ip)
    headers.set('X-Forwarded-For', ip)
  }
  headers.set('X-Forwarded-Proto', 'https')
  headers.set('X-Forwarded-Host', incoming.host)
  // Proves to the backend that this request came through the proxy (so X-Real-IP can be trusted).
  if (env.PROXY_SECRET) headers.set('X-Proxy-Secret', env.PROXY_SECRET)

  const hasBody = !['GET', 'HEAD'].includes(request.method)
  try {
    const res = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      redirect: 'manual',
    })
    // Pass the backend's response through untouched (status, Cache-Control: no-store, etc.).
    return new Response(res.body, { status: res.status, statusText: res.statusText, headers: res.headers })
  } catch (e) {
    // Backend asleep/unreachable: the app shows "temporarily unavailable" for 502/503/504.
    return Response.json({ error: 'Techy is temporarily unavailable. Please try again in a moment.' }, { status: 502 })
  }
}
