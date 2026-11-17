// Generates Techy's app icons (the ">_" mark) as PNGs, with no image libraries.
// Run: npm run icons   (writes into public/icons/)
//
// Shapes are drawn with signed-distance functions and 4x4 supersampling for smooth edges.
//  - icon-*.png           "any" purpose: rounded tile with transparent corners
//  - maskable-*.png       full-bleed square; the mark sits well inside the 80% safe zone so
//                          Android can crop it to a circle/squircle without clipping
//  - apple-touch-icon.png opaque 180x180 (iOS rounds the corners itself and dislikes transparency)
import { writeFileSync, mkdirSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons')
const BG = [0x16, 0x16, 0x1b]     // --ink
const ACCENT = [0xff, 0x5a, 0x1f] // --accent

// ---- geometry, in unit coordinates (0..1) ----
const STROKE = 0.085
const CHEVRON = [[0.27, 0.33], [0.45, 0.5], [0.27, 0.67]]
const UNDERSCORE = [[0.535, 0.67], [0.73, 0.67]]

function segDist(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax, dy = by - ay
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}
function inMark(x, y, scale) {
  // scale the mark around the centre (used to shrink it a little for maskable icons)
  const ux = 0.5 + (x - 0.5) / scale, uy = 0.5 + (y - 0.5) / scale
  const r = STROKE / 2
  return segDist(ux, uy, CHEVRON[0], CHEVRON[1]) <= r || segDist(ux, uy, CHEVRON[1], CHEVRON[2]) <= r
    || segDist(ux, uy, UNDERSCORE[0], UNDERSCORE[1]) <= r
}
function inRoundedRect(x, y, radius) {
  const qx = Math.abs(x - 0.5) - (0.5 - radius), qy = Math.abs(y - 0.5) - (0.5 - radius)
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius <= 0
}

function render(size, { radius = 0, markScale = 1 }) {
  const px = Buffer.alloc(size * size * 4)
  const S = 4 // supersamples per axis
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let bg = 0, mark = 0
      for (let sy = 0; sy < S; sy++) for (let sx = 0; sx < S; sx++) {
        const ux = (x + (sx + 0.5) / S) / size, uy = (y + (sy + 0.5) / S) / size
        if (radius === 0 || inRoundedRect(ux, uy, radius)) {
          bg++
          if (inMark(ux, uy, markScale)) mark++
        }
      }
      const n = S * S, a = bg / n, m = bg ? mark / bg : 0
      const i = (y * size + x) * 4
      for (let c = 0; c < 3; c++) px[i + c] = Math.round(BG[c] * (1 - m) + ACCENT[c] * m)
      px[i + 3] = Math.round(a * 255)
    }
  }
  return encodePng(size, size, px)
}

// ---- minimal PNG encoder (RGBA, 8-bit) ----
const CRC_TABLE = new Uint32Array(256).map((_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})
function crc32(buf) {
  let c = 0xffffffff
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}
function encodePng(w, h, rgba) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8; ihdr[9] = 6 // 8-bit RGBA
  const raw = Buffer.alloc((w * 4 + 1) * h)
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4)
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ])
}

// ---- outputs ----
mkdirSync(OUT, { recursive: true })
const files = {
  'icon-192.png': render(192, { radius: 0.22 }),
  'icon-512.png': render(512, { radius: 0.22 }),
  'maskable-192.png': render(192, { markScale: 0.86 }),
  'maskable-512.png': render(512, { markScale: 0.86 }),
  'apple-touch-icon.png': render(180, {}),
  'favicon-32.png': render(32, { radius: 0.22 }),
}
for (const [name, png] of Object.entries(files)) {
  writeFileSync(join(OUT, name), png)
  console.log(`wrote public/icons/${name} (${png.length} bytes)`)
}

// Scalable favicon for modern browsers (same geometry).
const pts = (p) => p.map(([x, y]) => `${x * 100},${y * 100}`).join(' ')
writeFileSync(join(OUT, 'icon.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <rect width="100" height="100" rx="22" fill="#16161b"/>
  <g fill="none" stroke="#ff5a1f" stroke-width="${STROKE * 100}" stroke-linecap="round" stroke-linejoin="round">
    <polyline points="${pts(CHEVRON)}"/>
    <line x1="${UNDERSCORE[0][0] * 100}" y1="${UNDERSCORE[0][1] * 100}" x2="${UNDERSCORE[1][0] * 100}" y2="${UNDERSCORE[1][1] * 100}"/>
  </g>
</svg>
`)
console.log('wrote public/icons/icon.svg')
