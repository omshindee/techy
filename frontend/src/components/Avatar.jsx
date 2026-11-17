import { useEffect, useState } from 'react'
import { mediaUrl } from '../services/api.js'

const HUES = [222, 20, 262, 140, 190, 330, 24, 205]

/** Round profile photo, falling back to a colored initial when there's no photo (or it fails to load). */
export default function Avatar({ username = '?', url, size = 32, className = '' }) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [url]) // retry when the picture changes
  const hue = HUES[[...username].reduce((a, c) => a + c.charCodeAt(0), 0) % HUES.length]
  const style = { width: size, height: size, fontSize: size * 0.42 }
  if (url && !failed) {
    return <img className={`avatar ${className}`} style={style} src={mediaUrl(url)} alt="" onError={() => setFailed(true)} />
  }
  return (
    <span className={`avatar avatar-initial ${className}`} style={{ ...style, background: `hsl(${hue} 50% 42%)` }} aria-hidden="true">
      {username[0]?.toUpperCase()}
    </span>
  )
}
