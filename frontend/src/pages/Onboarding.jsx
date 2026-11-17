import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ProfileForm from '../components/ProfileForm.jsx'
import { PeopleList } from './People.jsx'
import { useAuth } from '../context/AuthContext.jsx'

export default function Onboarding() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState(1)

  return (
    <div className="narrow">
      <ol className="steps" aria-label="Setup progress">
        <li className={step === 1 ? 'on' : 'done'}>Your profile</li>
        <li className={step === 2 ? 'on' : ''}>Follow people</li>
      </ol>
      {step === 1 ? (
        <>
          <h1 className="page-title">Welcome to Techy, {user.username}</h1>
          <p className="muted lede">Add a photo and a few skills so other developers know what you work on.</p>
          <div className="card form-card">
            <ProfileForm initial={user} submitLabel="Save and continue" onSaved={() => setStep(2)}
              extraAction={<button type="button" className="btn btn-quiet" onClick={() => setStep(2)}>Skip for now</button>} />
          </div>
        </>
      ) : (
        <>
          <h1 className="page-title">Follow some developers</h1>
          <p className="muted lede">Their posts show up first in your feed.</p>
          <div className="card list-card"><PeopleList /></div>
          <button className="btn btn-accent btn-block sticky-cta" onClick={() => navigate('/')}>Go to my feed</button>
        </>
      )}
    </div>
  )
}
