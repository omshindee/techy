import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { CloseIcon } from './Icons.jsx'

// Open modals, innermost last: Escape closes only the top one, page scroll unlocks when none are left.
const stack = []

/** Centered dialog over a dimmed page. Closes on Escape, backdrop click, or the ✕ button. */
export default function Modal({ title, onClose, children, className = '', showClose = true }) {
  const ref = useRef(null)
  // Keep the latest onClose without re-running the mount effect (callers pass inline arrows).
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    const prevFocus = document.activeElement
    const me = {}
    const onKey = (e) => { if (e.key === 'Escape' && stack[stack.length - 1] === me) closeRef.current() }
    document.addEventListener('keydown', onKey)
    stack.push(me)
    document.body.style.overflow = 'hidden'
    ref.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      stack.splice(stack.indexOf(me), 1)
      if (stack.length === 0) document.body.style.overflow = ''
      prevFocus?.focus?.()
    }
  }, [])

  return createPortal(
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      {showClose && (
        <button className="modal-x" onClick={onClose} aria-label="Close"><CloseIcon size={26} /></button>
      )}
      <div className={`modal ${className}`} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} ref={ref}>
        {title && <header className="modal-title"><h2>{title}</h2></header>}
        {children}
      </div>
    </div>,
    document.body,
  )
}

/** A stack of full-width options, like a mobile action sheet. `options`: [{ label, onClick, danger }] */
export function ActionSheet({ options, onClose }) {
  return (
    <Modal onClose={onClose} className="action-sheet" showClose={false} title="">
      {options.map((o) => (
        <button key={o.label} className={o.danger ? 'danger' : o.strong ? 'strong' : ''}
          onClick={() => { onClose(); o.onClick() }}>
          {o.label}
        </button>
      ))}
      <button onClick={onClose}>Cancel</button>
    </Modal>
  )
}
