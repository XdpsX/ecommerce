import type { GetCsrf200 } from './generated/model'
import { type NormalizedProblem, normalizeProblem } from './problem'
import axios, { type AxiosRequestConfig, CanceledError } from 'axios'

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: NormalizedProblem['fieldErrors']
  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors?: NormalizedProblem['fieldErrors'],
  ) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}
let generation = 0
const pending = new Set<AbortController>()
let unauthorized: (() => void) | undefined
export function onUnauthorized(handler: (() => void) | undefined) {
  unauthorized = handler
}
export function advanceSessionGeneration() {
  generation++
  for (const controller of pending) controller.abort()
  pending.clear()
}

type RequestOptions = Omit<AxiosRequestConfig, 'signal'> & {
  signal?: AbortSignal | undefined
}
export async function apiClient<T>(
  config: RequestOptions,
  options?: RequestOptions,
): Promise<T> {
  const { signal: requestSignal, ...request } = { ...config, ...options }
  const destination = new URL(
    axios.getUri({ ...request, url: request.url ?? '/' }),
    window.location.origin,
  )
  if (destination.origin !== window.location.origin)
    throw new Error('Only same-origin API requests are allowed')
  const epoch = generation
  const controller = new AbortController()
  pending.add(controller)
  const signal = requestSignal
    ? AbortSignal.any([requestSignal, controller.signal])
    : controller.signal
  try {
    const headers = { ...request.headers }
    if (
      !['GET', 'HEAD', 'OPTIONS'].includes(
        (request.method ?? 'GET').toUpperCase(),
      )
    ) {
      const csrf = await axios.get<GetCsrf200>('/bff/csrf', {
        baseURL: window.location.origin,
        withCredentials: true,
        signal,
      })
      if (
        csrf.data.headerName !== 'X-CSRF-TOKEN' ||
        typeof csrf.data.token !== 'string' ||
        !csrf.data.token
      )
        throw new Error('Invalid CSRF response')
      Object.assign(headers, { 'X-CSRF-TOKEN': csrf.data.token })
    }
    const response = await axios({
      ...request,
      headers,
      withCredentials: true,
      signal,
    })
    if (epoch !== generation) throw new CanceledError('Session changed')
    if (response.status !== 204 && typeof response.data === 'string')
      throw new Error('Expected JSON response')

    return response.data as T
  } catch (error) {
    if (epoch !== generation || axios.isCancel(error))
      throw new CanceledError('Session changed or request cancelled')
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 0
      const problem = normalizeProblem(error.response?.data, status)
      if (status === 401 && destination.pathname.startsWith('/api/')) {
        window.setTimeout(() => {
          if (epoch === generation) unauthorized?.()
        }, 0)
      }
      if (epoch !== generation) throw new CanceledError('Session changed')
      throw new ApiError(
        status,
        problem.code,
        problem.message,
        problem.fieldErrors,
      )
    }
    throw error
  } finally {
    pending.delete(controller)
  }
}
