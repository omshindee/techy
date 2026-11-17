/** The Techy wordmark: a terminal prompt followed by the name. */
export default function Brand({ size = 'md' }) {
  return (
    <span className={`brand brand-${size}`}>
      <span className="brand-mark" aria-hidden="true">&gt;_</span>Techy
    </span>
  )
}
