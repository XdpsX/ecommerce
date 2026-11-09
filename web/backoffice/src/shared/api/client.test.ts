import {
  advanceSessionGeneration,
  apiClient,
  ApiError,
  onUnauthorized,
} from './client'
import { server } from '@/test/server'
import axios from 'axios'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  advanceSessionGeneration()
  onUnauthorized(undefined)
})
describe('browser API boundary', () => {
  it('adds current CSRF to unsafe same-origin requests', async () => {
    server.use(
      http.get('*/bff/csrf', () =>
        HttpResponse.json({ token: 'csrf-new', headerName: 'X-CSRF-TOKEN' }),
      ),
      http.post('*/bff/logout', ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-new')
        expect(request.headers.has('Authorization')).toBe(false)

        return new HttpResponse(null, { status: 204 })
      }),
    )
    await expect(
      apiClient({ url: '/bff/logout', method: 'POST' }),
    ).resolves.toBe('')
    await expect(
      apiClient({ url: 'https://external.example/probe' }),
    ).rejects.toThrow('same-origin')
  })
  it('rejects an external baseURL before fetching or attaching a CSRF token', async () => {
    let csrfRequests = 0
    let externalRequests = 0
    server.use(
      http.get('*/bff/csrf', () => {
        csrfRequests++

        return HttpResponse.json({
          token: 'csrf-new',
          headerName: 'X-CSRF-TOKEN',
        })
      }),
      http.post('https://external.example/api/probe', ({ request }) => {
        externalRequests++
        expect(request.headers.has('X-CSRF-TOKEN')).toBe(false)

        return HttpResponse.json({ ok: true })
      }),
    )

    await expect(
      apiClient({
        url: '/api/probe',
        baseURL: 'https://external.example',
        method: 'POST',
      }),
    ).rejects.toThrow('same-origin')

    expect(csrfRequests).toBe(0)
    expect(externalRequests).toBe(0)
  })
  it('keeps permission denial separate from authentication and retains validation fields', async () => {
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    server.use(
      http.get('*/api/probe', () =>
        HttpResponse.json(
          {
            type: 'urn:ecommerce:problem:forbidden',
            title: 'Forbidden',
            status: 403,
            code: 'FORBIDDEN',
            detail: 'Denied',
            errors: [
              {
                location: 'query',
                name: 'page',
                code: 'INVALID_VALUE',
                message: 'Invalid',
              },
            ],
          },
          { status: 403 },
        ),
      ),
    )
    await expect(apiClient({ url: '/api/probe' })).rejects.toMatchObject({
      status: 403,
      code: 'FORBIDDEN',
      fieldErrors: [{ name: 'page' }],
    })
    expect(unauthorized).not.toHaveBeenCalled()
  })
  it('requests session reconciliation for a downstream 401 without replay', async () => {
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    let count = 0
    server.use(
      http.get('*/api/probe', () => {
        count++

        return new HttpResponse(null, { status: 401 })
      }),
    )
    await expect(apiClient({ url: '/api/probe' })).rejects.toBeInstanceOf(
      ApiError,
    )
    await vi.waitFor(() => expect(unauthorized).toHaveBeenCalledTimes(1))
    expect(unauthorized).toHaveBeenCalledTimes(1)
    expect(count).toBe(1)
  })
  it('ignores a delayed 401 callback after the session generation changes', async () => {
    let runScheduled: (() => void) | undefined
    const timer = vi
      .spyOn(window, 'setTimeout')
      .mockImplementation((handler) => {
        if (typeof handler === 'function') runScheduled = handler as () => void

        return 0
      })
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    server.use(
      http.get('*/api/probe', () => new HttpResponse(null, { status: 401 })),
    )

    await expect(apiClient({ url: '/api/probe' })).rejects.toBeInstanceOf(
      ApiError,
    )
    expect(runScheduled).toBeDefined()

    advanceSessionGeneration()
    runScheduled?.()

    expect(unauthorized).not.toHaveBeenCalled()
    timer.mockRestore()
  })
  it('uses the current unauthorized handler when a delayed 401 callback runs', async () => {
    let runScheduled: (() => void) | undefined
    const timer = vi
      .spyOn(window, 'setTimeout')
      .mockImplementation((handler) => {
        if (typeof handler === 'function') runScheduled = handler as () => void

        return 0
      })
    const firstHandler = vi.fn()
    const currentHandler = vi.fn()
    onUnauthorized(firstHandler)
    server.use(
      http.get('*/api/probe', () => new HttpResponse(null, { status: 401 })),
    )

    await expect(apiClient({ url: '/api/probe' })).rejects.toBeInstanceOf(
      ApiError,
    )
    onUnauthorized(currentHandler)
    runScheduled?.()

    expect(firstHandler).not.toHaveBeenCalled()
    expect(currentHandler).toHaveBeenCalledOnce()
    timer.mockRestore()
  })
  it('rejects responses and errors from a previous session generation', async () => {
    let ready: (() => void) | undefined
    let release: (() => void) | undefined
    const entered = new Promise<void>((resolve) => {
      ready = resolve
    })
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    server.use(
      http.get('*/api/slow', async () => {
        ready?.()
        await gate

        return new HttpResponse(null, { status: 401 })
      }),
    )
    const result = apiClient({ url: '/api/slow' }).catch(
      (error: unknown) => error,
    )
    await entered
    advanceSessionGeneration()
    release?.()
    expect(axios.isCancel(await result)).toBe(true)
    expect(unauthorized).not.toHaveBeenCalled()
  })
  it('normalizes network failures and rejects HTML success payloads', async () => {
    server.use(
      http.get('*/api/offline', () => HttpResponse.error()),
      http.get('*/api/html', () => HttpResponse.html('<html>Login</html>')),
    )
    await expect(apiClient({ url: '/api/offline' })).rejects.toMatchObject({
      status: 0,
      code: 'UNKNOWN_ERROR',
    })
    await expect(apiClient({ url: '/api/html' })).rejects.toThrow(
      'Expected JSON',
    )
  })
})
