/** The Techy wordmark: the app's emoji mascot (cut out, no background) followed by the name. */
export default function Brand({ size = 'md' }) {
  return (
    <span className={`brand brand-${size}`}>
      <img className="brand-mark" src="/icons/logo-64.png" srcSet="/icons/logo-64.png 1x, /icons/logo-256.png 3x" alt="" aria-hidden="true" />Techy
    </span>
  )
}
