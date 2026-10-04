// The one place that talks to the backend: a single POST /api/summarise.
// Every failure becomes an ApiError with a stable `code`, so the UI can branch on the code
// (see errors.js) and never has to read message text.

export const API_BASE = import.meta.env?.VITE_API_BASE || 'http://localhost:8080'

// The backend can legitimately need over two minutes in the worst case (60 s read timeout, a 2 s pause,
// a retry, then possibly a fallback model), so we wait 3 minutes before giving up.
export const CLIENT_TIMEOUT_MS = 180_000

export class ApiError extends Error {
  constructor(code, message, status = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code // e.g. "NO_ENGLISH_CAPTIONS", or "NETWORK" / "CLIENT_TIMEOUT" (client-side)
    this.status = status // HTTP status, or null when no response arrived
  }
}

/** True when the user pressed Cancel (not an error to show). */
export function isCancelled(error) {
  return error?.name === 'AbortError'
}

/**
 * POST /api/summarise.
 * @param {{url: string, transcript?: string}} payload  transcript is only sent when it is not blank
 * @param {{signal?: AbortSignal, timeoutMs?: number, fetchImpl?: typeof fetch}} options
 *        fetchImpl exists so tests can pass a fake fetch (the tests never call the real backend)
 * @returns the parsed success response; throws ApiError (or an AbortError when cancelled)
 */
export async function summarise({ url, transcript }, options = {}) {
  const { signal, timeoutMs = CLIENT_TIMEOUT_MS, fetchImpl = fetch } = options

  const timeout = AbortSignal.timeout(timeoutMs)
  const combined = signal ? AbortSignal.any([signal, timeout]) : timeout
  const body = transcript && transcript.trim() ? { url, transcript } : { url }

  let response
  try {
    response = await fetchImpl(`${API_BASE}/api/summarise`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: combined,
    })
  } catch (error) {
    if (signal?.aborted) throw new DOMException('Request cancelled', 'AbortError')
    if (timeout.aborted) {
      throw new ApiError('CLIENT_TIMEOUT', 'The server did not answer in time.')
    }
    // fetch rejects with a TypeError when the server is down, blocked by CORS, or offline.
    throw new ApiError('NETWORK', error?.message || 'Could not reach the server.')
  }

  let data = null
  try {
    data = await response.json()
  } catch {
    data = null // not JSON (for example an HTML error page from a proxy)
  }

  if (!response.ok) {
    throw new ApiError(
      data?.error || `HTTP_${response.status}`,
      data?.message || `The server answered with HTTP ${response.status}.`,
      response.status,
    )
  }
  if (!data || !data.summary) {
    throw new ApiError('INVALID_RESPONSE', 'The server answer did not contain a summary.', response.status)
  }
  return data
}
