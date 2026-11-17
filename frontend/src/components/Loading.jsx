export default function Loading({ label = 'Loading' }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      <span className="sr-only">{label}…</span>
    </div>
  )
}
