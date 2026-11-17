import { useEffect, useState } from 'react'
import Modal from './Modal.jsx'
import UserCard from './UserCard.jsx'
import Loading from './Loading.jsx'
import { api } from '../services/api.js'

/** Followers / Following list for a profile, opened from the stats row. */
export default function UserListModal({ username, kind, onClose }) {
  const [users, setUsers] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = kind === 'followers' ? api.followers(username) : api.following(username)
    load.then((u) => !cancelled && setUsers(u)).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [username, kind])

  return (
    <Modal title={kind === 'followers' ? 'Followers' : 'Following'} onClose={onClose} className="list-modal">
      <div className="list-modal-body">
        {error && <p className="error">{error}</p>}
        {!users && !error && <Loading />}
        {users?.length === 0 && (
          <p className="empty-small">
            {kind === 'followers' ? 'No followers yet.' : 'Not following anyone yet.'}
          </p>
        )}
        {users?.map((u) => <UserCard key={u.id} user={u} onNavigate={onClose} />)}
      </div>
    </Modal>
  )
}
