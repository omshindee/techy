import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Loading from '../components/Loading.jsx'
import PostCard from '../components/PostCard.jsx'
import { api } from '../services/api.js'

/** One post with its comments open, e.g. opened from a notification. */
export default function PostPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [post, setPost] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setPost(null); setError('')
    api.post(id).then((p) => !cancelled && setPost(p)).catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [id])

  if (error) {
    return (
      <div className="narrow">
        <div className="card empty">
          <h2>Post not available</h2>
          <p className="muted">It may have been deleted.</p>
          <Link to="/" className="btn btn-ghost">Back to home</Link>
        </div>
      </div>
    )
  }
  if (!post) return <Loading />
  return (
    <div className="narrow">
      <PostCard key={post.id} post={post} startWithComments onChange={setPost} onDelete={() => navigate('/', { replace: true })} />
    </div>
  )
}
