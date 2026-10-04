// Turns an error code into what the user sees. Adding a new backend error = adding one row here.
//
// kind     'input'      the user can fix it (a different link, paste a transcript)
//          'transient'  worth trying again
//          'info'       a normal outcome, nothing to retry (for example "not a DSA lecture")
//          'server'     a setup problem on the server, not the user's fault
// actions  which buttons to show: 'retry' | 'paste' | 'fixUrl'
// showServerMessage  also show the backend's own message (useful when it carries a reason)
// showCode           show the error code (for server problems, so the developer can find it)

export const ERROR_TABLE = {
  // ----- the request itself -----
  INVALID_URL: {
    title: "That doesn't look like a YouTube video link",
    hint: 'Paste the full link, for example https://www.youtube.com/watch?v=...',
    kind: 'input',
    actions: ['fixUrl'],
  },
  VALIDATION_ERROR: {
    title: 'Please enter a YouTube link',
    showServerMessage: true,
    kind: 'input',
    actions: ['fixUrl'],
  },

  // ----- getting the transcript -----
  TRANSCRIPT_UNAVAILABLE: {
    title: "Couldn't get a transcript for this video",
    hint: 'You can paste the transcript yourself instead.',
    showServerMessage: true,
    kind: 'input',
    actions: ['paste'],
  },
  NO_ENGLISH_CAPTIONS: {
    title: 'This video has no English captions',
    hint: 'Only English captions are supported. Paste an English transcript instead.',
    kind: 'input',
    actions: ['paste'],
  },
  NO_CAPTIONS: {
    title: 'This video has no captions',
    hint: 'There is nothing to fetch. Paste a transcript instead.',
    kind: 'input',
    actions: ['paste'],
  },
  VIDEO_UNAVAILABLE: {
    title: "This video can't be accessed",
    hint: 'It may be private, removed, age-restricted or blocked. Check the link, or paste a transcript.',
    kind: 'input',
    actions: ['paste'],
  },
  YOUTUBE_REFUSED: {
    title: 'YouTube is refusing requests from the server',
    hint: 'This usually passes after a while. Try again later, or paste the transcript.',
    kind: 'transient',
    actions: ['retry', 'paste'],
  },
  YOUTUBE_UNREACHABLE: {
    title: "Couldn't reach YouTube",
    hint: 'The server could not get the transcript right now. Try again, or paste the transcript.',
    kind: 'transient',
    actions: ['retry', 'paste'],
  },

  // ----- the summary -----
  NOT_A_DSA_LECTURE: {
    title: "This doesn't look like a DSA lecture",
    hint: 'Try a lecture about data structures or algorithms.',
    showServerMessage: true, // the model's own reason
    kind: 'info',
    actions: [],
  },
  SUMMARY_BLOCKED: {
    title: 'Gemini declined to summarise this video',
    hint: 'Try a different video.',
    kind: 'info',
    actions: [],
  },
  GEMINI_UNAVAILABLE: {
    title: 'Gemini is busy right now',
    hint: 'The server already retried automatically. Wait a moment and try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  GEMINI_TIMEOUT: {
    title: 'Gemini took too long to answer',
    hint: 'Try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  GEMINI_RATE_LIMITED: {
    title: 'Too many requests right now',
    hint: 'Wait a minute, then try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  GEMINI_QUOTA_EXCEEDED: {
    title: 'The Gemini limit has been reached',
    hint: 'The daily quota or credit is used up. Try again after it resets.',
    kind: 'server',
    showCode: true,
    actions: [],
  },
  SUMMARY_TRUNCATED: {
    title: 'The summary came back incomplete',
    hint: 'Try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  INVALID_MODEL_RESPONSE: {
    title: 'The summary came back in an unreadable form',
    hint: 'Try again.',
    kind: 'transient',
    actions: ['retry'],
  },

  // ----- server setup problems (not the user's fault) -----
  SUMMARISER_NOT_CONFIGURED: {
    title: 'The server is not set up for summaries',
    hint: 'This is a setup problem, not something you did: the Gemini API key is missing on the server.',
    kind: 'server',
    showCode: true,
    actions: [],
  },
  GEMINI_AUTH_FAILED: {
    title: "The server's Gemini key was rejected",
    hint: 'This is a setup problem, not something you did. The key is invalid, expired or has no access.',
    kind: 'server',
    showCode: true,
    actions: [],
  },
  GEMINI_MODEL_NOT_FOUND: {
    title: 'The configured Gemini model was not found',
    hint: 'This is a setup problem, not something you did. Check the gemini.model property on the server.',
    kind: 'server',
    showCode: true,
    actions: [],
  },
  GEMINI_BAD_REQUEST: {
    title: 'Gemini rejected the request',
    hint: 'This is a server-side problem, not something you did. See the server log.',
    kind: 'server',
    showCode: true,
    actions: [],
  },
  INTERNAL_ERROR: {
    title: 'Something went wrong on the server',
    hint: 'Try again.',
    kind: 'transient',
    actions: ['retry'],
  },

  // ----- raised by the frontend itself -----
  NETWORK: {
    title: "Can't reach the server",
    hint: 'Is the backend running? Start it and try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  CLIENT_TIMEOUT: {
    title: 'No response after 3 minutes',
    hint: 'The server took too long. Try again.',
    kind: 'transient',
    actions: ['retry'],
  },
  INVALID_RESPONSE: {
    title: 'The server answer was not understood',
    hint: 'Try again.',
    kind: 'transient',
    actions: ['retry'],
  },
}

// Used for any code that is not in the table (for example a new backend code added later).
const UNKNOWN = {
  title: 'Something went wrong',
  showServerMessage: true,
  kind: 'transient',
  actions: ['retry'],
}

/**
 * @param {{code?: string, message?: string}} error  usually an ApiError
 * @returns {{code, title, hint, serverMessage, kind, actions, showCode}} everything the ErrorPanel needs
 */
export function describeError(error) {
  const code = error?.code || 'UNKNOWN'
  const entry = ERROR_TABLE[code] || UNKNOWN
  return {
    code,
    title: entry.title,
    hint: entry.hint || '',
    serverMessage: entry.showServerMessage ? error?.message || '' : '',
    kind: entry.kind,
    actions: entry.actions,
    showCode: Boolean(entry.showCode),
  }
}
