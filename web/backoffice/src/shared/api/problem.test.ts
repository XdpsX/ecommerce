import { normalizeProblem } from './problem'
import i18n from '@/app/i18n'
import { describe, expect, it } from 'vitest'

describe('normalizeProblem', () => {
  it('keeps safe structured details and uses the actual HTTP status', () => {
    expect(
      normalizeProblem(
        {
          type: 'urn:ecommerce:problem:validation-failed',
          title: 'Invalid input',
          status: 400,
          detail: 'Check the submitted values.',
          code: 'VALIDATION_FAILED',
          errors: [
            {
              location: 'body',
              pointer: '/name',
              code: 'REQUIRED',
              message: 'Enter a name.',
            },
          ],
          futureField: true,
        },
        422,
      ),
    ).toEqual({
      status: 422,
      code: 'VALIDATION_FAILED',
      message: 'Check the submitted values.',
      fieldErrors: [
        {
          location: 'body',
          pointer: '/name',
          code: 'REQUIRED',
          message: 'Enter a name.',
        },
      ],
    })
  })

  it('uses a safe fallback for malformed and non-JSON responses', () => {
    expect(
      normalizeProblem({ detail: '<script>unsafe</script>' }, 503),
    ).toEqual({
      status: 503,
      code: 'UNKNOWN_ERROR',
      message: 'The request could not be completed. Please try again.',
      fieldErrors: undefined,
    })
  })

  it('localizes the safe fallback while retaining machine-readable codes', async () => {
    await i18n.changeLanguage('vi')

    expect(normalizeProblem(null, 503)).toEqual({
      status: 503,
      code: 'UNKNOWN_ERROR',
      message: 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.',
      fieldErrors: undefined,
    })

    await i18n.changeLanguage('en')
  })

  it.each([
    ['a null entry', [null]],
    [
      'a numeric body pointer',
      [
        {
          location: 'body',
          pointer: 12,
          code: 'INVALID',
          message: 'Invalid value.',
        },
      ],
    ],
    [
      'an object message',
      [
        {
          location: 'body',
          pointer: '/name',
          code: 'INVALID',
          message: { text: 'Invalid value.' },
        },
      ],
    ],
    [
      'a parameter error with the wrong location',
      [
        {
          location: 'cookie',
          name: 'session',
          code: 'INVALID',
          message: 'Invalid value.',
        },
      ],
    ],
    [
      'a field error with an unsupported property',
      [
        {
          location: 'body',
          pointer: '/name',
          code: 'INVALID',
          message: 'Invalid value.',
          extra: true,
        },
      ],
    ],
    ['an empty errors array', []],
  ])('rejects malformed field errors containing %s', (_description, errors) => {
    expect(
      normalizeProblem(
        {
          type: 'urn:ecommerce:problem:validation-failed',
          title: 'Invalid input',
          status: 400,
          detail: 'Check the submitted values.',
          code: 'VALIDATION_FAILED',
          errors,
        },
        400,
      ),
    ).toEqual({
      status: 400,
      code: 'UNKNOWN_ERROR',
      message: 'The request could not be completed. Please try again.',
      fieldErrors: undefined,
    })
  })

  it('accepts parameter errors that match the shared schema', () => {
    expect(
      normalizeProblem(
        {
          type: 'urn:ecommerce:problem:validation-failed',
          title: 'Invalid input',
          status: 400,
          detail: 'Check the submitted values.',
          code: 'VALIDATION_FAILED',
          errors: [
            {
              location: 'query',
              name: 'page',
              code: 'OUT_OF_RANGE',
              message: 'Enter a positive page number.',
            },
          ],
        },
        400,
      ).fieldErrors,
    ).toEqual([
      {
        location: 'query',
        name: 'page',
        code: 'OUT_OF_RANGE',
        message: 'Enter a positive page number.',
      },
    ])
  })
})
