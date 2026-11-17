// Keep in sync with backend Categories.java. Each category gets a "syntax highlight" hue.
export const CATEGORIES = [
  'C++', 'Java', 'DSA', 'Linux', 'Operating Systems', 'Web Development', 'AI/ML', 'DevOps', 'Tech', 'Memes',
]

export const CATEGORY_COLORS = {
  'C++': '#3b5bdb',
  Java: '#d9480f',
  DSA: '#7048e8',
  Linux: '#2f9e44',
  'Operating Systems': '#0c8599',
  'Web Development': '#c2255c',
  'AI/ML': '#e8590c',
  DevOps: '#1971c2',
  Tech: '#495057',
  Memes: '#f08c00',
}

const SHORT = { 'Operating Systems': 'OS', 'Web Development': 'Web' }

export const colorFor = (category) => CATEGORY_COLORS[category] || '#5c6b82'

/** Fits inside a small circle: "Operating Systems" -> "OS". */
export const shortName = (category) => SHORT[category] || category
