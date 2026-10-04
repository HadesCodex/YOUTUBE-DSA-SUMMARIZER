import { describe, expect, it } from 'vitest'
import { ApiError } from './api.js'
import { ERROR_TABLE, describeError } from './errors.js'

// Every error code the backend can return (backend: GlobalExceptionHandler, SummariseException,
// TranscriptUnavailableException, InvalidUrlException), plus the codes the frontend raises itself.
const BACKEND_CODES = [
  'INVALID_URL',
  'VALIDATION_ERROR',
  'TRANSCRIPT_UNAVAILABLE',
  'NO_ENGLISH_CAPTIONS',
  'NO_CAPTIONS',
  'VIDEO_UNAVAILABLE',
  'YOUTUBE_REFUSED',
  'YOUTUBE_UNREACHABLE',
  'NOT_A_DSA_LECTURE',
  'SUMMARY_BLOCKED',
  'GEMINI_UNAVAILABLE',
  'GEMINI_TIMEOUT',
  'GEMINI_RATE_LIMITED',
  'GEMINI_QUOTA_EXCEEDED',
  'SUMMARY_TRUNCATED',
  'INVALID_MODEL_RESPONSE',
  'SUMMARISER_NOT_CONFIGURED',
  'GEMINI_AUTH_FAILED',
  'GEMINI_MODEL_NOT_FOUND',
  'GEMINI_BAD_REQUEST',
  'INTERNAL_ERROR',
]
const CLIENT_CODES = ['NETWORK', 'CLIENT_TIMEOUT', 'INVALID_RESPONSE']
const KINDS = ['input', 'transient', 'info', 'server']
const ACTIONS = ['retry', 'paste', 'fixUrl']

describe('error table', () => {
  it.each([...BACKEND_CODES, ...CLIENT_CODES])('has a complete entry for %s', (code) => {
    const entry = ERROR_TABLE[code]
    expect(entry, `missing entry for ${code}`).toBeDefined()
    expect(entry.title.length).toBeGreaterThan(5)
    expect(KINDS).toContain(entry.kind)
    expect(Array.isArray(entry.actions)).toBe(true)
    entry.actions.forEach((action) => expect(ACTIONS).toContain(action))
    // every entry must say something to the user besides the title
    expect(Boolean(entry.hint) || Boolean(entry.showServerMessage)).toBe(true)
  })

  it('has no entry that is not a known code (catches typos in the table)', () => {
    const known = new Set([...BACKEND_CODES, ...CLIENT_CODES])
    Object.keys(ERROR_TABLE).forEach((code) => expect(known.has(code), `unexpected ${code}`).toBe(true))
  })
})

describe('describeError', () => {
  it('offers the paste box for every transcript problem', () => {
    for (const code of ['TRANSCRIPT_UNAVAILABLE', 'NO_ENGLISH_CAPTIONS', 'NO_CAPTIONS', 'VIDEO_UNAVAILABLE', 'YOUTUBE_REFUSED', 'YOUTUBE_UNREACHABLE']) {
      expect(describeError({ code }).actions, code).toContain('paste')
    }
  })

  it('explains the English-only rule for NO_ENGLISH_CAPTIONS', () => {
    const info = describeError(new ApiError('NO_ENGLISH_CAPTIONS', 'server text'))
    expect(info.title).toMatch(/no English captions/i)
    expect(info.hint).toMatch(/only English/i)
  })

  it('NOT_A_DSA_LECTURE shows the model reason and offers no retry', () => {
    const info = describeError(new ApiError('NOT_A_DSA_LECTURE', 'This is a cooking tutorial.', 422))
    expect(info.kind).toBe('info')
    expect(info.serverMessage).toBe('This is a cooking tutorial.')
    expect(info.actions).toEqual([])
  })

  it('GEMINI_UNAVAILABLE says Gemini is busy and offers retry', () => {
    const info = describeError(new ApiError('GEMINI_UNAVAILABLE', 'x', 502))
    expect(info.title).toMatch(/busy/i)
    expect(info.actions).toEqual(['retry'])
  })

  it('NETWORK and CLIENT_TIMEOUT offer retry', () => {
    expect(describeError({ code: 'NETWORK' }).actions).toEqual(['retry'])
    expect(describeError({ code: 'CLIENT_TIMEOUT' }).actions).toEqual(['retry'])
  })

  it('server setup problems show the code for the developer and no buttons', () => {
    for (const code of ['SUMMARISER_NOT_CONFIGURED', 'GEMINI_AUTH_FAILED', 'GEMINI_MODEL_NOT_FOUND', 'GEMINI_BAD_REQUEST']) {
      const info = describeError({ code })
      expect(info.kind, code).toBe('server')
      expect(info.showCode, code).toBe(true)
      expect(info.actions, code).toEqual([])
    }
  })

  it('only shows the backend message where it adds information', () => {
    expect(describeError({ code: 'NO_CAPTIONS', message: 'raw server text' }).serverMessage).toBe('')
    expect(describeError({ code: 'TRANSCRIPT_UNAVAILABLE', message: 'raw server text' }).serverMessage).toBe('raw server text')
  })

  it('falls back to a generic retryable error for an unknown code', () => {
    const info = describeError({ code: 'SOMETHING_NEW', message: 'a new backend problem' })
    expect(info.title).toBe('Something went wrong')
    expect(info.serverMessage).toBe('a new backend problem')
    expect(info.actions).toEqual(['retry'])
  })

  it('copes with a missing error object', () => {
    expect(describeError(undefined).actions).toEqual(['retry'])
  })
})
