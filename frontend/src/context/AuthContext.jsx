import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api } from '../services/api.js'
import { clearAuth, getStoredUser, getToken, setAuth, storeUser } from '../utils/auth.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser())
  const [ready, setReady] = useState(!getToken())
  // Public settings: captcha site key and whether email confirmation is required.
  const [config, setConfig] = useState({ captchaSiteKey: null, emailVerificationRequired: false })

  useEffect(() => {
    api.config().then(setConfig).catch(() => {})
  }, [])

  // Refresh the stored user from the server on first load.
  useEffect(() => {
    if (!getToken()) return
    api.me()
      .then((u) => { storeUser(u); setUser(u) })
      // A 401 is already handled in api.js (clears auth, redirects to /login).
      // Anything else (backend down, network blip) keeps the stored session.
      .catch(() => { if (!getStoredUser()) { clearAuth(); setUser(null) } })
      .finally(() => setReady(true))
  }, [])

  const login = (token, u) => { setAuth(token, u); setUser(u) }
  const logout = () => { clearAuth(); setUser(null) }
  const updateUser = (u) => { storeUser(u); setUser(u) }
  /** Re-reads your account, e.g. after confirming your email in another tab. */
  const refresh = useCallback(() => {
    if (!getToken()) return Promise.resolve()
    return api.me().then((u) => { storeUser(u); setUser(u) }).catch(() => {})
  }, [])

  return (
    <AuthContext.Provider value={{ user, ready, config, login, logout, updateUser, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
