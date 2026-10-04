import { BTN_GHOST, BTN_PRIMARY, CARD } from '../ui.js'
import { AlertCircleIcon, ClipboardIcon, LinkIcon, YoutubeIcon, ZapIcon } from './Icons.jsx'

// The input card, laid out as a compact bar on wider screens:
//   row 1: the link field and the Summarise button side by side (stacked on phones)
//   row 2: the "fetch / paste" switch and "Try an example"
//   then, only in paste mode, the transcript box at full width
// Obvious mistakes (a bad link, an empty paste box) get an inline message before anything is sent.
// The link is required in both modes: the backend uses it to get the video id.

function ModeButton({ active, onClick, icon: Icon, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`inline-flex flex-1 items-center justify-center gap-2 whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors sm:flex-none sm:px-3 ${
        active
          ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white'
          : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
      }`}
    >
      <Icon className="hidden h-4 w-4 sm:block" />
      {children}
    </button>
  )
}

function FieldError({ id, children }) {
  return (
    <p id={id} role="alert" className="mt-2 flex items-start gap-1.5 text-sm text-red-700 dark:text-red-300">
      <AlertCircleIcon className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{children}</span>
    </p>
  )
}

const FIELD =
  'w-full rounded-xl border bg-white text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-indigo-500 disabled:bg-slate-100 disabled:text-slate-500 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500 dark:disabled:bg-slate-900'

export default function UrlForm({
  url,
  onUrlChange,
  onUrlBlur,
  urlError,
  transcript,
  onTranscriptChange,
  transcriptError,
  pasteOpen,
  setPasteOpen,
  onSubmit,
  onExample,
  loading,
  urlInputRef,
  transcriptRef,
}) {
  function handleSubmit(event) {
    event.preventDefault()
    if (!loading) onSubmit()
  }

  return (
    <form onSubmit={handleSubmit} noValidate className={`${CARD} p-5 sm:p-6`}>
      <label htmlFor="video-url" className="mb-2 block text-sm font-semibold text-slate-900 dark:text-white">
        YouTube lecture link
      </label>

      {/* Row 1: the field takes all the spare width, the button keeps its own size. */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="relative min-w-0 flex-1">
          <LinkIcon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
          <input
            id="video-url"
            ref={urlInputRef}
            type="text"
            inputMode="url"
            autoComplete="off"
            spellCheck="false"
            placeholder="https://www.youtube.com/watch?v=..."
            value={url}
            onChange={(event) => onUrlChange(event.target.value)}
            onBlur={onUrlBlur}
            disabled={loading}
            aria-invalid={Boolean(urlError)}
            aria-describedby={urlError ? 'url-error' : undefined}
            className={`${FIELD} py-3 pl-11 pr-3.5 text-base ${
              urlError ? 'border-red-400 dark:border-red-500/70' : 'border-slate-300 dark:border-slate-700'
            }`}
          />
        </div>
        <button type="submit" disabled={loading} className={`${BTN_PRIMARY} !py-3 !text-base sm:shrink-0 sm:!px-6`}>
          {loading ? 'Summarising...' : 'Summarise lecture'}
        </button>
      </div>
      {urlError && <FieldError id="url-error">{urlError}</FieldError>}

      {/* Row 2: where the transcript comes from, and the example shortcut. */}
      <div className="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <div
            role="group"
            aria-label="Where the transcript comes from"
            className="flex w-full rounded-lg bg-slate-100 p-1 sm:inline-flex sm:w-auto dark:bg-slate-800"
          >
            <ModeButton active={!pasteOpen} onClick={() => setPasteOpen(false)} icon={YoutubeIcon}>
              <span className="sm:hidden">YouTube captions</span>
              <span className="hidden sm:inline">Fetch from YouTube</span>
            </ModeButton>
            <ModeButton active={pasteOpen} onClick={() => setPasteOpen(true)} icon={ClipboardIcon}>
              Paste transcript
            </ModeButton>
          </div>
          {!pasteOpen && (
            <p className="text-xs text-slate-600 dark:text-slate-400">
              Captions are fetched from YouTube. English captions only.
            </p>
          )}
        </div>
        <button type="button" onClick={onExample} disabled={loading} className={`${BTN_GHOST} !px-2.5 !py-1.5`}>
          <ZapIcon className="h-4 w-4" />
          Try an example
        </button>
      </div>

      {pasteOpen && (
        <div className="mt-4">
          <label htmlFor="transcript" className="mb-2 block text-sm font-semibold text-slate-900 dark:text-white">
            English transcript
          </label>
          <textarea
            id="transcript"
            ref={transcriptRef}
            rows={8}
            placeholder="Paste the lecture transcript here. The link above is still needed (it is used for the video id)."
            value={transcript}
            onChange={(event) => onTranscriptChange(event.target.value)}
            disabled={loading}
            aria-invalid={Boolean(transcriptError)}
            aria-describedby={transcriptError ? 'transcript-error' : 'transcript-help'}
            className={`${FIELD} px-3.5 py-3 text-sm leading-relaxed ${
              transcriptError ? 'border-red-400 dark:border-red-500/70' : 'border-slate-300 dark:border-slate-700'
            }`}
          />
          {transcriptError ? (
            <FieldError id="transcript-error">{transcriptError}</FieldError>
          ) : (
            <p id="transcript-help" className="mt-2 text-xs text-slate-600 dark:text-slate-400">
              With a pasted transcript the video is not fetched from YouTube.
            </p>
          )}
        </div>
      )}
    </form>
  )
}
