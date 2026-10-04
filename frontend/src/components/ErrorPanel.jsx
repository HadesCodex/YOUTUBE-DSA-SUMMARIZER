import { describeError } from '../errors.js'
import { BTN_PRIMARY, BTN_SECONDARY } from '../ui.js'
import { AlertCircleIcon, AlertTriangleIcon, ClipboardIcon, EditIcon, InfoIcon, RefreshIcon } from './Icons.jsx'

// One failure as a friendly card: an icon, what happened, what to do, and only the buttons that fit.
// The error code decides everything (errors.js); the colour follows the kind of problem:
//   amber    something you can fix or retry (a bad link, no captions, Gemini busy)
//   red      a failure on the server that you cannot fix from here
//   neutral  a normal outcome, nothing to retry (for example "not a DSA lecture")

const LOOK = {
  input: {
    box: 'border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100',
    icon: 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300',
    Icon: AlertTriangleIcon,
  },
  transient: {
    box: 'border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100',
    icon: 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300',
    Icon: AlertTriangleIcon,
  },
  server: {
    box: 'border-red-200 bg-red-50 text-red-950 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-100',
    icon: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300',
    Icon: AlertCircleIcon,
  },
  info: {
    box: 'border-slate-200 bg-white text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100',
    icon: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
    Icon: InfoIcon,
  },
}

export default function ErrorPanel({ error, onRetry, onPaste, onFixUrl }) {
  const info = describeError(error)
  const look = LOOK[info.kind] || LOOK.transient
  const { Icon } = look
  const retryFirst = info.actions.includes('retry')

  return (
    <div role="alert" className={`fade-in flex gap-4 rounded-2xl border p-5 shadow-sm sm:p-6 ${look.box}`}>
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${look.icon}`}>
        <Icon className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <h2 className="text-base font-semibold">{info.title}</h2>
        {info.hint && <p className="mt-1 text-sm opacity-90">{info.hint}</p>}
        {info.serverMessage && (
          <p className="mt-3 rounded-lg bg-black/5 px-3 py-2 text-sm italic dark:bg-white/10">{info.serverMessage}</p>
        )}
        {info.showCode && (
          <p className="mt-3 text-xs opacity-80">
            Error code for the developer: <code className="font-mono font-semibold">{info.code}</code>
          </p>
        )}

        {info.actions.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2">
            {info.actions.includes('retry') && (
              <button type="button" onClick={onRetry} className={BTN_PRIMARY}>
                <RefreshIcon className="h-4 w-4" />
                Try again
              </button>
            )}
            {info.actions.includes('paste') && (
              <button type="button" onClick={onPaste} className={retryFirst ? BTN_SECONDARY : BTN_PRIMARY}>
                <ClipboardIcon className="h-4 w-4" />
                Paste a transcript
              </button>
            )}
            {info.actions.includes('fixUrl') && (
              <button type="button" onClick={onFixUrl} className={BTN_PRIMARY}>
                <EditIcon className="h-4 w-4" />
                Edit the link
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
