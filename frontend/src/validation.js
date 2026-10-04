// Checks done in the browser BEFORE anything is sent, so an obvious mistake gets an instant inline message.
// The link rule is the same one the backend uses (VideoIdExtractor), so a link accepted here is accepted there.
// The backend stays the source of truth for everything else.

/** A short, well-known lecture used by the "Try an example" button. */
export const EXAMPLE_URL = 'https://www.youtube.com/watch?v=MHf6awe89xw'

// Same patterns as the backend: watch?v=ID, youtu.be/ID, shorts/ID, embed/ID (ID = 11 characters).
const YOUTUBE_LINK =
  /(?:youtube\.com\/watch\?(?:.*&)?v=|youtu\.be\/|youtube\.com\/shorts\/|youtube\.com\/embed\/)([A-Za-z0-9_-]{11})/

/** The 11-character video id inside a YouTube link, or null. */
export function extractVideoId(url) {
  const match = YOUTUBE_LINK.exec(String(url ?? '').trim())
  return match ? match[1] : null
}

/** '' when the link looks fine, otherwise a message for the field. */
export function validateUrl(url) {
  const text = String(url ?? '').trim()
  if (!text) return 'Enter a YouTube link.'
  if (!extractVideoId(text)) {
    return "That doesn't look like a YouTube video link. Use a link like https://www.youtube.com/watch?v=... or https://youtu.be/..."
  }
  return ''
}

/** '' when something was pasted, otherwise a message for the paste box. */
export function validateTranscript(text) {
  if (!String(text ?? '').trim()) {
    return 'Paste the transcript text, or switch back to fetching the captions from YouTube.'
  }
  return ''
}
