import { createContext, useContext } from 'react'

export type SessionStatus =
  'loading' | 'authenticated' | 'unauthenticated' | 'unavailable'
type SessionContextValue = {
  status: SessionStatus
  subject: string | undefined
  logoutPending: boolean
  logoutFailed: boolean
  logout: () => Promise<void>
  revalidate: () => Promise<void>
  retry: () => void
}
export const SessionContext = createContext<SessionContextValue | null>(null)
export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('SessionProvider is required')

  return value
}
