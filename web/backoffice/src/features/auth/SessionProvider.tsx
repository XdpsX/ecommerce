import { SessionContext, type SessionStatus } from './session'
import {
  advanceSessionGeneration,
  ApiError,
  onUnauthorized,
} from '@/shared/api/client'
import {
  getSession,
  logout as endSession,
} from '@/shared/api/generated/backoffice'
import type { GetSession200 } from '@/shared/api/generated/model'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react'

async function loadSession({
  signal,
}: {
  signal: AbortSignal
}): Promise<GetSession200 | null> {
  advanceSessionGeneration()
  try {
    const result = await getSession(undefined, signal)
    if (
      !result ||
      typeof result.subject !== 'string' ||
      !/^[0-9a-f-]{36}$/i.test(result.subject)
    )
      throw new Error('Invalid session')

    return result
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}
const sessionOptions = {
  queryKey: ['session'] as const,
  queryFn: loadSession,
  retry: false,
  staleTime: 0,
}
export function SessionProvider({ children }: { children: ReactNode }) {
  const cache = useQueryClient()
  const [logoutPending, setLogoutPending] = useState(false)
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [reconcilingUnauthorized, setReconcilingUnauthorized] = useState(false)
  const unauthorizedReconciliation = useRef(false)
  const clearSensitive = useCallback(async () => {
    advanceSessionGeneration()
    await cache.cancelQueries({
      predicate: (query) => query.queryKey[0] !== 'session',
    })
    cache.removeQueries({
      predicate: (query) => query.queryKey[0] !== 'session',
    })
  }, [cache])
  const checkSession = useCallback(
    async (context: { signal: AbortSignal }) => {
      const previousSession = cache.getQueryData<GetSession200 | null>([
        'session',
      ])
      const hasPreviousSession = previousSession !== undefined

      const nextSession = await loadSession(context)

      if (
        !hasPreviousSession ||
        previousSession?.subject !== nextSession?.subject
      ) {
        await clearSensitive()
      }

      return nextSession
    },
    [cache, clearSensitive],
  )
  const session = useQuery({
    ...sessionOptions,
    queryFn: checkSession,
    enabled: !logoutPending,
    refetchOnWindowFocus: 'always',
  })
  const subject = session.data?.subject
  const revalidate = useCallback(async () => {
    await cache.query({ ...sessionOptions, queryFn: checkSession })
  }, [cache, checkSession])
  useEffect(() => {
    onUnauthorized(() => {
      if (!unauthorizedReconciliation.current) {
        unauthorizedReconciliation.current = true
        setReconcilingUnauthorized(true)
        void cache.invalidateQueries({ queryKey: ['session'] }).finally(() => {
          unauthorizedReconciliation.current = false
          setReconcilingUnauthorized(false)
        })
      }
    })

    return () => onUnauthorized(undefined)
  }, [cache])
  const performLogout = async () => {
    if (logoutPending) return
    setLogoutPending(true)
    setLogoutFailed(false)
    await clearSensitive()
    try {
      await endSession()
      await clearSensitive()
      cache.setQueryData(['session'], null)
    } catch {
      setLogoutFailed(true)
      try {
        await cache.query({ ...sessionOptions, queryFn: checkSession })
      } catch {
        /* unavailable is held by the query */
      }
    } finally {
      setLogoutPending(false)
    }
  }
  const status: SessionStatus =
    logoutPending ||
    session.isPending ||
    (session.isFetching && !(reconcilingUnauthorized && session.data))
      ? 'loading'
      : session.isError
        ? 'unavailable'
        : session.data
          ? 'authenticated'
          : 'unauthenticated'

  return (
    <SessionContext.Provider
      value={{
        status,
        subject,
        logoutPending,
        logoutFailed,
        logout: performLogout,
        retry: () => {
          void session.refetch()
        },
        revalidate,
      }}
    >
      {children}
    </SessionContext.Provider>
  )
}
