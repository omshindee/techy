import Brand from './Brand.jsx'

/** Sign-in / sign-up frame: a dark "terminal" panel introducing Techy beside the form. */
export default function AuthLayout({ title, children, footer }) {
  return (
    <div className="auth">
      <section className="auth-panel" aria-hidden="true">
        <Brand size="lg" />
        <p className="auth-tagline">The network for people who build things.</p>
        <pre className="auth-terminal">
          <span className="t-dim">~ $</span> techy post --photo homelab.jpg{'\n'}
          <span className="t-ok">✓</span> shared to <span className="t-tag">#devops</span>{'\n'}
          <span className="t-dim">~ $</span> techy follow priya_dev{'\n'}
          <span className="t-ok">✓</span> following priya_dev{'\n'}
          <span className="t-dim">~ $</span> techy feed --for-you{'\n'}
          <span className="t-dim">  ranked by people you follow + your channels</span>{'\n'}
          <span className="t-dim">~ $</span> <span className="cursor" />
        </pre>
      </section>
      <section className="auth-form">
        <div className="auth-mobile-brand">
          <Brand size="lg" />
          <p className="auth-tagline">The network for people who build things.</p>
        </div>
        <h1>{title}</h1>
        {children}
        <p className="auth-footer">{footer}</p>
      </section>
    </div>
  )
}
