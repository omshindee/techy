import { useEffect, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import Avatar from './Avatar.jsx'
import Brand from './Brand.jsx'
import { GearIcon, HomeIcon, LogOutIcon, PenIcon, SearchIcon, ShieldIcon, UsersIcon } from './Icons.jsx'
import { api } from '../services/api.js'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * App navigation. Wide screens: a left sidebar with labelled links and your account at the bottom.
 * Medium screens: the same sidebar, icons only. Phones: a slim top bar plus a bottom tab bar.
 */
export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const signOut = () => { logout(); navigate('/login') }
  const me = `/profile/${user.username}`
  const [openReports, setOpenReports] = useState(0)

  // Moderators see how many reports are waiting.
  useEffect(() => {
    if (!user.admin) return
    const load = () => api.openReportCount().then((r) => setOpenReports(r.open)).catch(() => {})
    load()
    const t = setInterval(load, 60_000)
    window.addEventListener('techy:reports-changed', load) // fired by the Moderation page
    return () => { clearInterval(t); window.removeEventListener('techy:reports-changed', load) }
  }, [user.admin])

  const links = [
    { to: '/', label: 'Home', Icon: HomeIcon, end: true },
    { to: '/people', label: 'People', Icon: UsersIcon },
    { to: '/settings', label: 'Settings', Icon: GearIcon },
    ...(user.admin ? [{ to: '/moderation', label: 'Moderation', Icon: ShieldIcon, badge: openReports }] : []),
  ]

  return (
    <>
      <aside className="sidenav" aria-label="Main navigation">
        <Link to="/" className="sidenav-brand" aria-label="Techy home"><Brand /></Link>
        <nav>
          {links.map(({ to, label, Icon, end, badge }) => (
            <NavLink key={to} to={to} end={end} className="sidenav-link" title={label}>
              <Icon size={22} /><span>{label}</span>
              {badge > 0 && <em className="count-badge" aria-label={`${badge} open`}>{badge}</em>}
            </NavLink>
          ))}
          <NavLink to={me} className="sidenav-link" title="Profile">
            <Avatar username={user.username} url={user.avatarUrl} size={22} /><span>Profile</span>
          </NavLink>
        </nav>
        <Link to="/new" className="btn btn-accent sidenav-new" title="New post">
          <PenIcon size={18} /><span>New post</span>
        </Link>
        <div className="sidenav-me">
          <Link to={me} className="sidenav-me-link">
            <Avatar username={user.username} url={user.avatarUrl} size={36} />
            <span className="sidenav-me-name">
              <strong>{user.displayName || user.username}</strong>
              <small>{user.skills?.slice(0, 2).join(' · ') || 'View profile'}</small>
            </span>
          </Link>
          <button className="icon-button" onClick={signOut} aria-label="Sign out" title="Sign out"><LogOutIcon size={20} /></button>
        </div>
      </aside>

      <header className="topbar">
        <Link to="/" aria-label="Techy home"><Brand size="sm" /></Link>
        <span className="topbar-actions">
          {user.admin && (
            <Link to="/moderation" className="icon-button" aria-label={`Moderation (${openReports} open)`}>
              <ShieldIcon size={20} />{openReports > 0 && <em className="count-badge">{openReports}</em>}
            </Link>
          )}
          <Link to="/people" className="icon-button" aria-label="Search people"><SearchIcon size={20} /></Link>
        </span>
      </header>

      <nav className="tabbar" aria-label="Main navigation">
        <NavLink to="/" end aria-label="Home"><HomeIcon size={22} /></NavLink>
        <NavLink to="/people" aria-label="People"><UsersIcon size={22} /></NavLink>
        <NavLink to="/new" className="tabbar-new" aria-label="New post"><PenIcon size={20} /></NavLink>
        <NavLink to={me} aria-label="Your profile"><Avatar username={user.username} url={user.avatarUrl} size={26} /></NavLink>
        <NavLink to="/settings" aria-label="Settings"><GearIcon size={22} /></NavLink>
      </nav>
    </>
  )
}
