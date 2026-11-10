import { useSession } from './session'
import { SessionProvider } from './SessionProvider'
import { apiClient, ApiError } from '@/shared/api/client'
import { server } from '@/test/server'
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, expect, it, vi } from 'vitest'

afterEach(cleanup)
function Status() {
  const session = useSession()

  return (
    <output>
      {session.status === 'authenticated' ? session.subject : session.status}
    </output>
  )
}
it('removes identity-sensitive cached data when the session subject changes', async () => {
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const first = '11111111-1111-4111-8111-111111111111'
  const second = '22222222-2222-4222-8222-222222222222'
  server.use(
    http.get('*/bff/session', () => HttpResponse.json({ subject: first })),
  )
  render(
    <QueryClientProvider client={cache}>
      <SessionProvider>
        <Status />
      </SessionProvider>
    </QueryClientProvider>,
  )
  await screen.findByText(first)
  cache.setQueryData(['private-records'], 'previous user records')
  let entered: (() => void) | undefined
  let release: (() => void) | undefined
  const requestEntered = new Promise<void>((resolve) => {
    entered = resolve
  })
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  server.use(
    http.get('*/bff/session', async () => {
      entered?.()
      await gate

      return HttpResponse.json({ subject: second })
    }),
  )
  let refresh: Promise<void> | undefined
  await act(async () => {
    refresh = cache.invalidateQueries({ queryKey: ['session'] })
    await requestEntered
  })
  expect(cache.getQueryData(['private-records'])).toBe('previous user records')
  expect(screen.queryByText(first)).not.toBeInTheDocument()
  await act(async () => {
    release?.()
    await refresh
  })
  expect(await screen.findByText(second)).toBeInTheDocument()
  expect(cache.getQueryData(['private-records'])).toBeUndefined()
})

function PrivateRequest() {
  const session = useSession()
  const request = useQuery({
    queryKey: ['downstream-probe'],
    queryFn: () => apiClient({ url: '/api/probe' }),
    retry: false,
  })

  return (
    <output>
      {`${session.status}:${request.error instanceof ApiError ? request.error.status : 'pending'}`}
    </output>
  )
}

function ProtectedStatus() {
  const session = useSession()

  return session.status === 'authenticated' ? (
    <PrivateRequest />
  ) : (
    <output>{session.status}</output>
  )
}

it('keeps a downstream 401 error when session reconciliation confirms the session', async () => {
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  let sessionRequests = 0
  let downstreamRequests = 0
  server.use(
    http.get('*/bff/session', () => {
      sessionRequests++

      return HttpResponse.json({
        subject: '11111111-1111-4111-8111-111111111111',
      })
    }),
    http.get('*/api/probe', () => {
      downstreamRequests++

      return new HttpResponse(null, { status: 401 })
    }),
  )

  render(
    <QueryClientProvider client={cache}>
      <SessionProvider>
        <ProtectedStatus />
      </SessionProvider>
    </QueryClientProvider>,
  )

  await screen.findByText('authenticated:401')
  await vi.waitFor(() => expect(sessionRequests).toBe(2))
  await new Promise((resolve) => setTimeout(resolve, 100))

  expect(downstreamRequests).toBe(1)
  expect(screen.getByText('authenticated:401')).toBeInTheDocument()
})
