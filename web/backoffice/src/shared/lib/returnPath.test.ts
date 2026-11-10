import { getSafeReturnPath } from './returnPath'
import { describe, expect, it } from 'vitest'

describe('getSafeReturnPath', () => {
  it('keeps an internal path and its search and hash', () => {
    expect(getSafeReturnPath('/orders?state=pending#recent')).toBe(
      '/orders?state=pending#recent',
    )
  })

  it.each([
    'https://example.com',
    '//example.com/path',
    '/a/..//external.example/path',
    '/\\example.com',
    'orders',
    null,
  ])('falls back for an unsafe destination: %s', (value) => {
    expect(getSafeReturnPath(value)).toBe('/')
  })
})
