import { clearAuth, getToken } from '../utils/auth.js'

const BASE = import.meta.env.VITE_API_URL || '/api'

/**
 * @param body JSON body (object) — or pass `form` (FormData) for file uploads.
 * For FormData the browser sets the multipart Content-Type (with boundary) itself.
 */
async function request(path, { method = 'GET', body, form } = {}) {
  const token = getToken()
  let res
  try {
    res = await fetch(BASE + path, {
      method,
      headers: {
        ...(form ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: form ?? (body ? JSON.stringify(body) : undefined),
    })
  } catch {
    // fetch only throws when the request never reached a server (offline, DNS, dropped connection).
    throw new Error(navigator.onLine === false
      ? 'You’re offline. Check your connection and try again.'
      : 'Couldn’t reach Techy. Check your connection and try again.')
  }
  if ([502, 503, 504].includes(res.status)) {
    throw new Error('Techy is temporarily unavailable. Please try again in a moment.')
  }
  if (res.status === 401 && token) {
    // Token expired or invalid: drop it and send the user back to login.
    clearAuth()
    window.location.assign('/login')
    throw new Error('Your session expired. Please sign in again.')
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.error || `Request failed (${res.status})`)
  return data
}

/**
 * The backend returns uploaded images as "/api/media/<key>". When the API lives on another
 * origin (VITE_API_URL), rewrite that prefix so <img> tags point at the right host.
 */
export function mediaUrl(url) {
  if (!url || !url.startsWith('/api/') || BASE === '/api') return url
  return BASE.replace(/\/$/, '') + url.slice('/api'.length)
}

const enc = encodeURIComponent

export const api = {
  /** { captchaSiteKey, emailVerificationRequired } */
  config: () => request('/auth/config'),
  /** `extra`: { displayName, website (honeypot), captchaToken } */
  register: (username, email, password, extra = {}) =>
    request('/auth/register', { method: 'POST', body: { username, email, password, ...extra } }),
  login: (username, password) => request('/auth/login', { method: 'POST', body: { username, password } }),
  verifyEmail: (token) => request('/auth/verify-email', { method: 'POST', body: { token } }),
  forgotPassword: (email, captchaToken) => request('/auth/forgot-password', { method: 'POST', body: { email, captchaToken } }),
  resetPassword: (token, password) => request('/auth/reset-password', { method: 'POST', body: { token, password } }),
  resendVerification: () => request('/users/me/verification', { method: 'POST' }),

  me: () => request('/users/me'),
  updateMe: (profile) => request('/users/me', { method: 'PUT', body: profile }),
  uploadAvatar: (file) => {
    const form = new FormData()
    form.set('image', file, file.name || 'avatar.jpg')
    return request('/users/me/avatar', { method: 'PUT', form })
  },
  removeAvatar: () => request('/users/me/avatar', { method: 'DELETE' }),
  /** Server-side people search: { items, hasMore }. Empty query lists everyone, newest first. */
  searchUsers: (q = '', page = 0) => request(`/users?q=${enc(q)}&page=${page}`),
  suggestions: (limit = 5) => request(`/users/suggestions?limit=${limit}`),
  user: (username) => request(`/users/${enc(username)}`),
  block: (username) => request(`/users/${enc(username)}/block`, { method: 'POST' }),
  unblock: (username) => request(`/users/${enc(username)}/block`, { method: 'DELETE' }),
  blocked: () => request('/users/me/blocked'),
  follow: (username) => request(`/users/${enc(username)}/follow`, { method: 'POST' }),
  unfollow: (username) => request(`/users/${enc(username)}/follow`, { method: 'DELETE' }),
  followers: (username) => request(`/users/${enc(username)}/followers`),
  following: (username) => request(`/users/${enc(username)}/following`),

  feed: () => request('/feed'),
  posts: ({ category, username } = {}) => {
    const q = new URLSearchParams()
    if (category) q.set('category', category)
    if (username) q.set('username', username)
    const qs = q.toString()
    return request(`/posts${qs ? `?${qs}` : ''}`)
  },
  /** Text post, photo post, or photo with caption. `image` is an optional File/Blob. */
  createPost: (content, category, image) => {
    const form = new FormData()
    form.set('content', content)
    form.set('category', category)
    if (image) form.set('image', image, image.name || 'photo.jpg')
    return request('/posts', { method: 'POST', form })
  },
  deletePost: (id) => request(`/posts/${id}`, { method: 'DELETE' }),
  like: (id) => request(`/posts/${id}/like`, { method: 'POST' }),
  unlike: (id) => request(`/posts/${id}/like`, { method: 'DELETE' }),
  comments: (id) => request(`/posts/${id}/comments`),
  addComment: (id, content) => request(`/posts/${id}/comments`, { method: 'POST', body: { content } }),
  deleteComment: (postId, id) => request(`/posts/${postId}/comments/${id}`, { method: 'DELETE' }),

  /** targetType: POST | COMMENT | USER; reason: SPAM | HARASSMENT | HATE | NUDITY | VIOLENCE | SCAM | OTHER */
  report: (targetType, targetId, reason, details) =>
    request('/reports', { method: 'POST', body: { targetType, targetId, reason, details } }),

  // Direct messages
  inbox: () => request('/messages/conversations'),
  unreadMessages: () => request('/messages/unread-count'),
  /** { after } = only newer than that message id (polling); { before } = older page. */
  thread: (username, { after, before } = {}) => {
    const q = new URLSearchParams()
    if (after) q.set('after', after)
    if (before) q.set('before', before)
    const qs = q.toString()
    return request(`/messages/with/${enc(username)}${qs ? `?${qs}` : ''}`)
  },
  sendMessage: (username, content) => request(`/messages/with/${enc(username)}`, { method: 'POST', body: { content } }),

  // Moderators only
  reports: (status = 'open') => request(`/admin/reports?status=${status}`),
  openReportCount: () => request('/admin/reports/count'),
  resolveReport: (id, action) => request(`/admin/reports/${id}/resolve`, { method: 'POST', body: { action } }),
  suspend: (username) => request(`/admin/users/${enc(username)}/suspend`, { method: 'POST' }),
  unsuspend: (username) => request(`/admin/users/${enc(username)}/suspend`, { method: 'DELETE' }),
}
