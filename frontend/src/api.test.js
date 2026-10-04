import { describe, expect, it, vi } from 'vitest'
import { API_BASE, ApiError, CLIENT_TIMEOUT_MS, isCancelled, summarise } from './api.js'
import { sampleResult } from './fixtures.js'

// A fake fetch: these tests never touch the network, the backend or Gemini.
function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

/** A fetch that never answers until its signal aborts (to test cancel and timeout). */
function hangingFetch() {
  return (url, { signal }) =>
    new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason))
    })
}

describe('summarise: success', () => {
  it('POSTs JSON to /api/summarise and returns the parsed response', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, sampleResult()))

    const data = await summarise({ url: 'https://youtu.be/abc', transcript: '' }, { fetchImpl })

    expect(data.summary.topic).toBe('Binary Search')
    const [url, init] = fetchImpl.mock.calls[0]
    expect(url).toBe(`${API_BASE}/api/summarise`)
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
  })

  it('sends only the url when no transcript is pasted', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, sampleResult()))
    await summarise({ url: 'https://youtu.be/abc', transcript: '   ' }, { fetchImpl })
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({ url: 'https://youtu.be/abc' })
  })

  it('sends the pasted transcript when there is one', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, sampleResult()))
    await summarise({ url: 'https://youtu.be/abc', transcript: 'two pointers on a sorted array' }, { fetchImpl })
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
      url: 'https://youtu.be/abc',
      transcript: 'two pointers on a sorted array',
    })
  })
})

describe('summarise: backend errors become ApiError with the backend code', () => {
  it.each([
    [400, 'INVALID_URL'],
    [422, 'NO_ENGLISH_CAPTIONS'],
    [422, 'NOT_A_DSA_LECTURE'],
    [429, 'GEMINI_RATE_LIMITED'],
    [502, 'GEMINI_UNAVAILABLE'],
    [503, 'SUMMARISER_NOT_CONFIGURED'],
    [504, 'GEMINI_TIMEOUT'],
  ])('HTTP %i %s', async (status, code) => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(status, { error: code, message: `message for ${code}` }))

    const error = await summarise({ url: 'u' }, { fetchImpl }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe(code)
    expect(error.status).toBe(status)
    expect(error.message).toBe(`message for ${code}`)
  })

  it('uses HTTP_<status> when the error body is not JSON (for example an HTML error page)', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: false, status: 502, json: async () => { throw new SyntaxError('not json') } })

    const error = await summarise({ url: 'u' }, { fetchImpl }).catch((e) => e)

    expect(error.code).toBe('HTTP_502')
    expect(error.status).toBe(502)
  })

  it('treats a 200 without a summary as INVALID_RESPONSE', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, { videoId: 'x' }))
    const error = await summarise({ url: 'u' }, { fetchImpl }).catch((e) => e)
    expect(error.code).toBe('INVALID_RESPONSE')
  })
})

describe('summarise: failures before any answer', () => {
  it('a network failure (server down, CORS, offline) becomes NETWORK', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await summarise({ url: 'u' }, { fetchImpl }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('NETWORK')
    expect(error.status).toBeNull()
  })

  it('no answer within the time limit becomes CLIENT_TIMEOUT', async () => {
    const error = await summarise({ url: 'u' }, { fetchImpl: hangingFetch(), timeoutMs: 30 }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('CLIENT_TIMEOUT')
  })

  it('pressing Cancel gives an AbortError that is not an ApiError', async () => {
    const controller = new AbortController()
    const pending = summarise({ url: 'u' }, { fetchImpl: hangingFetch(), signal: controller.signal, timeoutMs: 5000 })

    controller.abort()
    const error = await pending.catch((e) => e)

    expect(isCancelled(error)).toBe(true)
    expect(error).not.toBeInstanceOf(ApiError)
  })

  it('the default time limit is 3 minutes', () => {
    expect(CLIENT_TIMEOUT_MS).toBe(180_000)
  })
})

describe('isCancelled', () => {
  it('is true only for an AbortError', () => {
    expect(isCancelled(new DOMException('x', 'AbortError'))).toBe(true)
    expect(isCancelled(new ApiError('NETWORK', 'x'))).toBe(false)
    expect(isCancelled(undefined)).toBe(false)
  })
})
