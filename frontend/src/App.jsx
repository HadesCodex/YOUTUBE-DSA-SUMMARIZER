import { useEffect, useRef, useState } from 'react'
import { ApiError, isCancelled, summarise } from './api.js'
import { CodeIcon } from './components/Icons.jsx'
import ErrorPanel from './components/ErrorPanel.jsx'
import LoadingPanel from './components/LoadingPanel.jsx'
import SummaryView from './components/SummaryView.jsx'
import ThemeToggle from './components/ThemeToggle.jsx'
import UrlForm from './components/UrlForm.jsx'
import { EXAMPLE_URL, validateTranscript, validateUrl } from './validation.js'

// One page. The whole screen is always in exactly one phase, held in plain useState:
//   idle     -> waiting for a link
//   loading  -> request running (can be cancelled)
//   success  -> `result` holds the summary
//   error    -> `error` holds an ApiError; ErrorPanel decides what to show
// Obvious input mistakes (empty or non-YouTube link, empty paste box) are caught first and shown inline
// under the field, so they never reach the backend.

const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

export default function App() {
  const [url, setUrl] = useState('')
  const [transcript, setTranscript] = useState('')
  const [pasteOpen, setPasteOpen] = useState(false)
  const [urlError, setUrlError] = useState('')
  const [transcriptError, setTranscriptError] = useState('')

  const [phase, setPhase] = useState('idle')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [focusRequest, setFocusRequest] = useState(null) // 'paste' | 'url' | null

  const abortRef = useRef(null)
  const urlInputRef = useRef(null)
  const transcriptRef = useRef(null)
  const resultRef = useRef(null)

  async function run() {
    const urlProblem = validateUrl(url)
    if (urlProblem) {
      setUrlError(urlProblem)
      setFocusRequest('url')
      return
    }
    if (pasteOpen) {
      const transcriptProblem = validateTranscript(transcript)
      if (transcriptProblem) {
        setTranscriptError(transcriptProblem)
        setFocusRequest('paste')
        return
      }
    }

    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller

    setUrlError('')
    setTranscriptError('')
    setPhase('loading')
    setError(null)
    setResult(null)
    try {
      const data = await summarise(
        { url: url.trim(), transcript: pasteOpen ? transcript : '' },
        { signal: controller.signal },
      )
      setResult(data)
      setPhase('success')
    } catch (e) {
      if (isCancelled(e)) {
        setPhase('idle') // the user pressed Cancel: nothing to report
        return
      }
      setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', e?.message || 'Unexpected error.'))
      setPhase('error')
    }
  }

  function cancel() {
    abortRef.current?.abort()
  }

  function openPasteBox() {
    setError(null)
    setPhase('idle')
    setPasteOpen(true)
    setFocusRequest('paste') // focus moves in the effect below, once the textarea exists
  }

  function editUrl() {
    setFocusRequest('url')
  }

  function useExample() {
    setUrl(EXAMPLE_URL)
    setUrlError('')
    setFocusRequest('url')
  }

  // "Summarise another": back to a clean form.
  function reset() {
    setResult(null)
    setError(null)
    setPhase('idle')
    setUrl('')
    setTranscript('')
    setPasteOpen(false)
    setUrlError('')
    setTranscriptError('')
    setFocusRequest('url')
    window.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }

  function handleUrlChange(value) {
    setUrl(value)
    if (urlError) setUrlError('')
  }

  // A gentle check when leaving the field: only once something has been typed.
  function handleUrlBlur() {
    if (url.trim()) setUrlError(validateUrl(url))
  }

  // The "Fetch from YouTube / Paste transcript" switch. Choosing paste puts the cursor in the box.
  function handleSource(openPaste) {
    setPasteOpen(openPaste)
    if (openPaste) setFocusRequest('paste')
    else setTranscriptError('')
  }

  function handleTranscriptChange(value) {
    setTranscript(value)
    if (transcriptError) setTranscriptError('')
  }

  // Moves keyboard focus after a render, when the element we want is guaranteed to be on the page.
  useEffect(() => {
    if (focusRequest === 'paste') transcriptRef.current?.focus()
    if (focusRequest === 'url') {
      urlInputRef.current?.focus()
      urlInputRef.current?.select()
    }
    if (focusRequest) setFocusRequest(null)
  }, [focusRequest])

  // After a successful summary, bring it into view and put the topic in the tab title.
  useEffect(() => {
    if (phase === 'success' && result) {
      document.title = `${result.summary.topic} - DSA Lecture Summariser`
      resultRef.current?.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' })
    } else {
      document.title = 'DSA Lecture Summariser'
    }
  }, [phase, result])

  return (
    <div className="relative min-h-screen overflow-x-clip">
      {/* The one decorative accent: a faint gradient wash behind the header. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-80 bg-gradient-to-b from-indigo-100/80 via-violet-50/50 to-transparent dark:from-indigo-500/15 dark:via-violet-500/5"
      />

      <main className="relative mx-auto max-w-6xl px-4 pb-20 pt-6 sm:px-6 sm:pt-10 lg:px-8">
        <header className="mb-8 flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-sm">
              <CodeIcon className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl dark:text-white">
                DSA Lecture Summariser
              </h1>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Revision notes from English-captioned DSA lectures.
              </p>
            </div>
          </div>
          <ThemeToggle />
        </header>

        <UrlForm
          url={url}
          onUrlChange={handleUrlChange}
          onUrlBlur={handleUrlBlur}
          urlError={urlError}
          transcript={transcript}
          onTranscriptChange={handleTranscriptChange}
          transcriptError={transcriptError}
          pasteOpen={pasteOpen}
          setPasteOpen={handleSource}
          onSubmit={run}
          onExample={useExample}
          loading={phase === 'loading'}
          urlInputRef={urlInputRef}
          transcriptRef={transcriptRef}
        />

        <div ref={resultRef} className="mt-6 scroll-mt-4">
          {phase === 'loading' && <LoadingPanel onCancel={cancel} />}
          {phase === 'error' && error && (
            <ErrorPanel error={error} onRetry={run} onPaste={openPasteBox} onFixUrl={editUrl} />
          )}
          {phase === 'success' && result && <SummaryView result={result} onReset={reset} />}
          {phase === 'idle' && (
            <p className="text-center text-sm text-slate-600 dark:text-slate-400">
              Only videos with English captions are supported. You can also paste a transcript yourself.
            </p>
          )}
        </div>
      </main>
    </div>
  )
}
