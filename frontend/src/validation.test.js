import { describe, expect, it } from 'vitest'
import { EXAMPLE_URL, extractVideoId, validateTranscript, validateUrl } from './validation.js'

// The accepted link forms mirror the backend's VideoIdExtractor, so a link that passes here passes there.
describe('extractVideoId (same rules as the backend)', () => {
  it.each([
    ['https://www.youtube.com/watch?v=MHf6awe89xw', 'MHf6awe89xw'],
    ['https://www.youtube.com/watch?list=PL123&v=MHf6awe89xw&t=10s', 'MHf6awe89xw'],
    ['https://youtu.be/dQw4w9WgXcQ', 'dQw4w9WgXcQ'],
    ['https://youtu.be/dQw4w9WgXcQ?si=abc', 'dQw4w9WgXcQ'],
    ['https://www.youtube.com/shorts/abcDEF12345', 'abcDEF12345'],
    ['https://www.youtube.com/embed/abcDEF12345', 'abcDEF12345'],
    ['https://m.youtube.com/watch?v=MHf6awe89xw', 'MHf6awe89xw'],
    ['  https://youtu.be/dQw4w9WgXcQ  ', 'dQw4w9WgXcQ'],
  ])('%s -> %s', (url, id) => {
    expect(extractVideoId(url)).toBe(id)
  })

  it.each([
    ['', null],
    ['hello', null],
    ['https://example.com/watch?v=MHf6awe89xw', null],
    ['https://www.youtube.com/watch?v=short', null],
    ['https://www.youtube.com/', null],
    [null, null],
    [undefined, null],
  ])('%s is not a video link', (url, id) => {
    expect(extractVideoId(url)).toBe(id)
  })

  it('the example link is itself valid', () => {
    expect(extractVideoId(EXAMPLE_URL)).toBe('MHf6awe89xw')
  })
})

describe('validateUrl', () => {
  it('accepts a good link', () => {
    expect(validateUrl('https://youtu.be/dQw4w9WgXcQ')).toBe('')
  })

  it('asks for a link when the field is empty or blank', () => {
    expect(validateUrl('')).toBe('Enter a YouTube link.')
    expect(validateUrl('   ')).toBe('Enter a YouTube link.')
  })

  it('explains what a valid link looks like when it is not a YouTube video link', () => {
    const message = validateUrl('https://example.com/video')
    expect(message).toMatch(/doesn't look like a YouTube video link/)
    expect(message).toMatch(/youtu\.be/)
  })
})

describe('validateTranscript', () => {
  it('accepts pasted text', () => {
    expect(validateTranscript('two pointers on a sorted array')).toBe('')
  })

  it('asks for text, or to switch back, when the paste box is empty', () => {
    expect(validateTranscript('')).toMatch(/Paste the transcript text/)
    expect(validateTranscript('  \n ')).toMatch(/switch back/)
  })
})
