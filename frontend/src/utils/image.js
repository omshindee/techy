// Client-side image preparation: phone photos are often 5–15 MB, so shrink them before upload.

export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const MAX_UPLOAD_BYTES = 8 * 1024 * 1024 // keep in sync with app.storage.max-image-bytes

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => { URL.revokeObjectURL(url); resolve(img) }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('That file could not be read as an image.')) }
    img.src = url
  })
}

function toBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

function rename(file, blob, type) {
  const ext = type === 'image/png' ? 'png' : 'jpg'
  const base = (file.name || 'photo').replace(/\.[^.]+$/, '')
  return new File([blob], `${base}.${ext}`, { type })
}

/**
 * Downscales a photo so its longest side is at most `maxSide`.
 * PNGs stay PNG (code screenshots look bad as JPEG); everything else becomes JPEG.
 * GIFs are passed through untouched so animations survive.
 */
export async function preparePostImage(file, maxSide = 1440) {
  if (!ACCEPTED_TYPES.includes(file.type)) throw new Error('Please choose a JPEG, PNG, GIF or WebP image.')
  if (file.type === 'image/gif') return checkSize(file)
  const img = await loadImage(file)
  const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight))
  const type = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  if (scale === 1 && file.size <= 1.5 * 1024 * 1024 && file.type !== 'image/webp') return file
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(img.naturalWidth * scale)
  canvas.height = Math.round(img.naturalHeight * scale)
  const ctx = canvas.getContext('2d')
  if (type === 'image/jpeg') { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height) }
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  const blob = await toBlob(canvas, type, 0.86)
  return checkSize(blob && blob.size < file.size ? rename(file, blob, type) : file)
}

/** Center-crops to a square and scales to `size` px — profile photos are always shown round. */
export async function prepareAvatar(file, size = 320) {
  if (!ACCEPTED_TYPES.includes(file.type)) throw new Error('Please choose a JPEG, PNG, GIF or WebP image.')
  const img = await loadImage(file)
  const side = Math.min(img.naturalWidth, img.naturalHeight)
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = Math.min(size, side)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, canvas.width, canvas.height)
  const blob = await toBlob(canvas, 'image/jpeg', 0.9)
  return checkSize(rename(file, blob, 'image/jpeg'))
}

function checkSize(file) {
  if (file.size > MAX_UPLOAD_BYTES) throw new Error('That image is too large (max 8 MB).')
  return file
}
