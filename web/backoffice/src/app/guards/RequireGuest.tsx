import { SessionState } from './SessionState'
import { useSession } from '@/features/auth/session'
import { Navigate } from '@tanstack/react-router'
import { type ReactNode } from 'react'

export function RequireGuest({ children }: { children: ReactNode }) {
  const { status } = useSession()
  if (status === 'loading' || status === 'unavailable') return <SessionState />
  if (status === 'authenticated') return <Navigate to="/" replace />

  return children
}
