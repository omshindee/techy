import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import UserCard from '../components/UserCard.jsx'
import Loading from '../components/Loading.jsx'
import { SearchIcon } from '../components/Icons.jsx'
import { api } from '../services/api.js'

/**
 * Server-side search by name, @username or skill, 20 at a time with "Load more".
 * An empty query lists everyone, newest first. Blocked and suspended accounts never appear.
 */
export function PeopleList({ query = '' }) {
  const [items, setItems] = useState(null)
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setError('')
    // Wait until typing pauses, so we don't search on every keystroke.
    const t = setTimeout(() => {
      api.searchUsers(query.trim(), 0)
        .then((res) => { if (!cancelled) { setItems(res.items); setHasMore(res.hasMore); setPage(0) } })
        .catch((e) => !cancelled && setError(e.message))
    }, query ? 250 : 0)
    return () => { cancelled = true; clearTimeout(t) }
  }, [query])

  async function more() {
    setLoadingMore(true)
    try {
      const res = await api.searchUsers(query.trim(), page + 1)
      setItems((prev) => [...prev, ...res.items.filter((u) => !prev.some((p) => p.id === u.id))])
      setHasMore(res.hasMore)
      setPage((p) => p + 1)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <>
      {error && <p className="error">{error}</p>}
      {!items && !error && <Loading label="Loading developers" />}
      {items && items.length === 0 && (
        <p className="empty-small">{query ? `Nobody matches “${query}”.` : 'No one else is here yet. Invite a friend.'}</p>
      )}
      <div className="people-list">{items?.map((u) => <UserCard key={u.id} user={u} />)}</div>
      {hasMore && (
        <div className="load-more">
          <button className="btn btn-ghost" onClick={more} disabled={loadingMore}>{loadingMore ? 'Loading…' : 'Load more'}</button>
        </div>
      )}
    </>
  )
}

export default function People() {
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') || '')
  useEffect(() => { setQuery(params.get('q') || '') }, [params])

  return (
    <div className="narrow">
      <h1 className="page-title">People</h1>
      <label className="search-box big">
        <SearchIcon size={18} />
        <input type="search" enterKeyHint="search" autoCapitalize="none" autoCorrect="off"
          value={query} placeholder="Search by name, @username or skill" aria-label="Search people"
          onChange={(e) => { setQuery(e.target.value); setParams(e.target.value ? { q: e.target.value } : {}, { replace: true }) }} />
      </label>
      <div className="card list-card"><PeopleList query={query} /></div>
    </div>
  )
}
