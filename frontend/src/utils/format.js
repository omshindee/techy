function parts(iso) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  return { seconds, minutes, hours, days }
}

/** Compact age for comments and lists: "now", "5m", "3h", "2d", "4w". */
export function timeAgo(iso) {
  const { seconds, minutes, hours, days } = parts(iso)
  if (seconds < 60) return 'now'
  if (minutes < 60) return `${minutes}m`
  if (hours < 24) return `${hours}h`
  if (days < 7) return `${days}d`
  return `${Math.floor(days / 7)}w`
}

const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`

/** Long age for the bottom of a post: "5 minutes ago", "2 days ago", "March 3". */
export function timeAgoLong(iso) {
  const { seconds, minutes, hours, days } = parts(iso)
  if (seconds < 60) return 'Just now'
  if (minutes < 60) return `${plural(minutes, 'minute')} ago`
  if (hours < 24) return `${plural(hours, 'hour')} ago`
  if (days < 7) return `${plural(days, 'day')} ago`
  const d = new Date(iso)
  const sameYear = d.getFullYear() === new Date().getFullYear()
  return d.toLocaleDateString(undefined, { month: 'long', day: 'numeric', ...(sameYear ? {} : { year: 'numeric' }) })
}

/** 1234 -> "1,234"; 12345 -> "12.3k". */
export function count(n) {
  if (n < 10000) return n.toLocaleString()
  if (n < 1e6) return `${(n / 1000).toFixed(n < 1e5 ? 1 : 0).replace(/\.0$/, '')}k`
  return `${(n / 1e6).toFixed(1).replace(/\.0$/, '')}m`
}
