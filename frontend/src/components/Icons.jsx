// Small outline icon set (24px grid, 1.8px stroke). `filled` switches to a solid variant where it exists.

function Svg({ size = 24, children, label, ...rest }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden={label ? undefined : true} role={label ? 'img' : undefined}
      aria-label={label} {...rest}>
      {children}
    </svg>
  )
}

export const HomeIcon = ({ filled, ...p }) => (
  <Svg {...p}>
    <path d="M3.5 10.2 12 3.5l8.5 6.7V20a.5.5 0 0 1-.5.5h-5v-6h-6v6H4a.5.5 0 0 1-.5-.5z"
      fill={filled ? 'currentColor' : 'none'} />
    {filled && <path d="M9 20.5v-6h6v6" stroke="var(--surface)" />}
  </Svg>
)

export const PlusSquareIcon = ({ filled, ...p }) => (
  <Svg {...p}>
    <rect x="3.5" y="3.5" width="17" height="17" rx="4.5" fill={filled ? 'currentColor' : 'none'} />
    <path d="M12 8v8M8 12h8" stroke={filled ? 'var(--surface)' : 'currentColor'} />
  </Svg>
)

export const CompassIcon = ({ filled, ...p }) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="8.75" fill={filled ? 'currentColor' : 'none'} />
    <path d="m15.5 8.5-2 5-5 2 2-5z" fill={filled ? 'var(--surface)' : 'none'} stroke={filled ? 'var(--surface)' : 'currentColor'} />
  </Svg>
)

export const HeartIcon = ({ filled, ...p }) => (
  <Svg {...p}>
    <path d="M12 20.3s-7.8-4.6-8.9-10A4.9 4.9 0 0 1 12 6.6a4.9 4.9 0 0 1 8.9 3.7c-1.1 5.4-8.9 10-8.9 10z"
      fill={filled ? 'currentColor' : 'none'} />
  </Svg>
)

export const CommentIcon = (p) => (
  <Svg {...p}>
    <path d="M20.6 15.6A8.7 8.7 0 1 0 17 19.4l3.9 1.1z" />
  </Svg>
)

export const MoreIcon = (p) => (
  <Svg {...p}>
    <circle cx="5.5" cy="12" r="1.3" fill="currentColor" stroke="none" />
    <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" />
    <circle cx="18.5" cy="12" r="1.3" fill="currentColor" stroke="none" />
  </Svg>
)

export const GridIcon = (p) => (
  <Svg {...p}>
    <rect x="3.5" y="3.5" width="17" height="17" rx="1" />
    <path d="M9.2 3.5v17M14.8 3.5v17M3.5 9.2h17M3.5 14.8h17" />
  </Svg>
)

export const GearIcon = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2.6-1.5L14 2.5h-4l-.4 2.5a7.6 7.6 0 0 0-2.6 1.5l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 3l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2.6 1.5l.4 2.5h4l.4-2.5a7.6 7.6 0 0 0 2.6-1.5l2.4 1 2-3.4z" />
  </Svg>
)

export const SearchIcon = (p) => (
  <Svg {...p}>
    <circle cx="10.5" cy="10.5" r="6.5" />
    <path d="m15.5 15.5 5 5" />
  </Svg>
)

export const CloseIcon = (p) => (
  <Svg {...p}>
    <path d="M5 5l14 14M19 5 5 19" />
  </Svg>
)

export const PhotoIcon = (p) => (
  <Svg {...p}>
    <rect x="3" y="4.5" width="18" height="15" rx="2.5" />
    <circle cx="8.5" cy="9.5" r="1.8" />
    <path d="m3.5 17.5 5.2-5 3.8 3.6 2.7-2.6 5.3 4.6" />
  </Svg>
)

export const CodeIcon = (p) => (
  <Svg {...p}>
    <path d="m8.5 7-5 5 5 5M15.5 7l5 5-5 5M13.5 4.5l-3 15" />
  </Svg>
)

export const GithubIcon = (p) => (
  <Svg {...p}>
    <path d="M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21" />
  </Svg>
)

export const UsersIcon = (p) => (
  <Svg {...p}>
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5" />
    <path d="M15.5 4.8a3.5 3.5 0 0 1 0 6.4M18 14.8c2 .7 3.2 2.5 3.5 5.2" />
  </Svg>
)

export const PenIcon = (p) => (
  <Svg {...p}>
    <path d="M4 20h4L19.5 8.5a2.8 2.8 0 0 0-4-4L4 16z" />
    <path d="m13.5 6.5 4 4" />
  </Svg>
)

export const LogOutIcon = (p) => (
  <Svg {...p}>
    <path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4M9 16l-4-4 4-4M5 12h11" />
  </Svg>
)

export const UserIcon = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 20.5c.8-3.8 4-6 8-6s7.2 2.2 8 6" />
  </Svg>
)

export const ListIcon = (p) => (
  <Svg {...p}>
    <path d="M9 6h11M9 12h11M9 18h11" />
    <circle cx="4.5" cy="6" r="1" fill="currentColor" />
    <circle cx="4.5" cy="12" r="1" fill="currentColor" />
    <circle cx="4.5" cy="18" r="1" fill="currentColor" />
  </Svg>
)

export const TerminalIcon = (p) => (
  <Svg {...p}>
    <rect x="2.5" y="4" width="19" height="16" rx="3" />
    <path d="m7 9.5 3 2.5-3 2.5M12.5 15h4.5" />
  </Svg>
)

export const ShieldIcon = (p) => (
  <Svg {...p}>
    <path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.9 7.5-9.5V6z" />
    <path d="m9 12 2.2 2.2L15.5 10" />
  </Svg>
)

export const ChatIcon = ({ filled, ...p }) => (
  <Svg {...p}>
    <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v9a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 3.5V17h0a1.5 1.5 0 0 1-1.5-1.5z" fill={filled ? "currentColor" : "none"} />
    {!filled && <path d="M8 8.5h8M8 12h5" />}
  </Svg>
)

export const SendIcon = (p) => (
  <Svg {...p}>
    <path d="M21 3 10.5 13.5M21 3l-6.5 18-4-7.5L3 9.5z" />
  </Svg>
)

export const BackIcon = (p) => (
  <Svg {...p}>
    <path d="M15 5l-7 7 7 7" />
  </Svg>
)
