import { SessionState } from './SessionState'
import { useSession } from '@/features/auth/session'
import { getSafeReturnPath } from '@/shared/lib/returnPath'
import { Navigate, useLocation } from '@tanstack/react-router'
import { type ReactNode, useEffect, useMemo, useState } from 'react'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { revalidate, status } = useSession()
  const location = useLocation()
  const [verifiedLocation, setVerifiedLocation] = useState<string>()
  const search = useMemo(() => {
    const returnTo = getSafeReturnPath(location.href)

    return returnTo === '/' ? {} : { returnTo }
  }, [location.href])
  useEffect(() => {
    let active = true
    void revalidate()
      .catch(() => undefined)
      .finally(() => {
        if (active) setVerifiedLocation(location.href)
      })

    return () => {
      active = false
    }
  }, [location.href, revalidate])

  if (
    verifiedLocation !== location.href ||
    status === 'loading' ||
    status === 'unavailable'
  ) {
    return <SessionState />
  }
  if (status === 'unauthenticated') {
    if (location.pathname === '/login') return null

    return <Navigate to="/login" search={search} replace />
  }

  return children
}
