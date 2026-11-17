import Modal from './Modal.jsx'
import PostCard from './PostCard.jsx'

/** A single post opened from the Photos tab, with its discussion already open. */
export default function PostModal({ post, onClose, onChange, onDelete }) {
  return (
    <Modal title={`Post by ${post.author.username}`} onClose={onClose} className="post-modal">
      <PostCard post={post} startWithComments onChange={onChange}
        onDelete={(id) => { onClose(); onDelete?.(id) }} />
    </Modal>
  )
}
