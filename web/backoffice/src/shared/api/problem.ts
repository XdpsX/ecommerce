import i18n from 'i18next'

type BodyFieldError = {
  location: 'body'
  pointer: string
  code: string
  message: string
}

type ParameterError = {
  location: 'query' | 'path' | 'header'
  name: string
  code: string
  message: string
}

type FieldError = BodyFieldError | ParameterError

export type ProblemDetails = {
  type: string
  title: string
  status: number
  detail: string
  code: string
  instance?: string
  errors?: FieldError[]
}

export type NormalizedProblem = {
  status: number
  code: string
  message: string
  fieldErrors: ProblemDetails['errors']
}

const fieldErrorCodePattern = /^[A-Z][A-Z0-9_]*$/

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasOnlyKeys(value: Record<string, unknown>, allowedKeys: string[]) {
  return Object.keys(value).every((key) => allowedKeys.includes(key))
}

function isFieldError(value: unknown): value is FieldError {
  if (
    !isRecord(value) ||
    typeof value.code !== 'string' ||
    !fieldErrorCodePattern.test(value.code) ||
    typeof value.message !== 'string'
  ) {
    return false
  }

  if (value.location === 'body') {
    return (
      typeof value.pointer === 'string' &&
      hasOnlyKeys(value, ['location', 'pointer', 'code', 'message'])
    )
  }

  return (
    (value.location === 'query' ||
      value.location === 'path' ||
      value.location === 'header') &&
    typeof value.name === 'string' &&
    hasOnlyKeys(value, ['location', 'name', 'code', 'message'])
  )
}

function isFieldErrors(value: unknown): value is FieldError[] {
  return Array.isArray(value) && value.length > 0 && value.every(isFieldError)
}

function isProblemDetails(value: unknown): value is ProblemDetails {
  return (
    isRecord(value) &&
    typeof value.type === 'string' &&
    typeof value.title === 'string' &&
    typeof value.status === 'number' &&
    Number.isInteger(value.status) &&
    value.status >= 400 &&
    value.status <= 599 &&
    typeof value.detail === 'string' &&
    typeof value.code === 'string' &&
    fieldErrorCodePattern.test(value.code) &&
    (value.errors === undefined || isFieldErrors(value.errors))
  )
}

export function normalizeProblem(
  value: unknown,
  httpStatus: number,
): NormalizedProblem {
  if (!isProblemDetails(value)) {
    return {
      status: httpStatus,
      code: 'UNKNOWN_ERROR',
      message: i18n.t('errors.requestFallback', { ns: 'common' }),
      fieldErrors: undefined,
    }
  }

  return {
    status: httpStatus,
    code: value.code,
    message: value.detail || i18n.t('errors.requestFallback', { ns: 'common' }),
    fieldErrors: value.errors,
  }
}
