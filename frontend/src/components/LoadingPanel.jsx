import { useEffect, useState } from 'react'
import useMediaQuery, { LG } from '../useMediaQuery.js'
import { BTN_SECONDARY, CARD } from '../ui.js'
import { XIcon } from './Icons.jsx'

// Shown while the request runs: a skeleton of the result cards (so the page does not jump when the answer
// arrives), an elapsed-seconds counter and a Cancel button. There is no real progress to report (the backend
// sends none), so there is deliberately no progress bar and the text says what is actually happening.

function Bar({ className = '' }) {
  return <div className={`h-3 rounded-full bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800 ${className}`} />
}

function SkeletonCard({ children }) {
  return <div className={`${CARD} p-5 sm:p-6`}>{children}</div>
}

function SkeletonHeading() {
  return (
    <div className="mb-4 flex items-center gap-2.5">
      <div className="h-7 w-7 rounded-lg bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
      <Bar className="w-28" />
    </div>
  )
}

// The same sections as the real result, as grey placeholders.
const problem = (
  <SkeletonCard key="problem">
    <SkeletonHeading />
    <div className="space-y-2.5">
      <Bar className="w-full" />
      <Bar className="w-5/6" />
    </div>
  </SkeletonCard>
)

const steps = (
  <SkeletonCard key="steps">
    <SkeletonHeading />
    <div className="space-y-3.5">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="h-6 w-6 shrink-0 rounded-full bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
          <Bar className={i % 2 ? 'w-4/5' : 'w-full'} />
        </div>
      ))}
    </div>
  </SkeletonCard>
)

const code = (
  <SkeletonCard key="code">
    <SkeletonHeading />
    <div className="rounded-xl bg-slate-900 p-4 dark:bg-black/50 dark:ring-1 dark:ring-slate-800">
      <div className="space-y-3">
        <div className="h-3 w-1/3 rounded-full bg-slate-700 motion-safe:animate-pulse" />
        <div className="h-3 w-2/3 rounded-full bg-slate-700 motion-safe:animate-pulse" />
        <div className="h-3 w-1/2 rounded-full bg-slate-700 motion-safe:animate-pulse" />
      </div>
    </div>
  </SkeletonCard>
)

const tiles = (
  <SkeletonCard key="tiles">
    <SkeletonHeading />
    <div className="grid grid-cols-2 gap-3 sm:gap-4">
      {[0, 1].map((i) => (
        <div key={i} className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
          <Bar className="w-16" />
          <div className="mt-3 h-8 w-24 rounded-lg bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
          <Bar className="mt-3 w-full" />
        </div>
      ))}
    </div>
  </SkeletonCard>
)

const edgeCases = (
  <SkeletonCard key="edge">
    <SkeletonHeading />
    <div className="space-y-2.5">
      <Bar className="w-full" />
      <Bar className="w-4/5" />
    </div>
  </SkeletonCard>
)

const revision = (
  <SkeletonCard key="revision">
    <SkeletonHeading />
    <div className="space-y-3">
      {[0, 1, 2].map((i) => (
        <Bar key={i} className={i === 1 ? 'w-5/6' : 'w-full'} />
      ))}
    </div>
  </SkeletonCard>
)

export default function LoadingPanel({ onCancel }) {
  const [seconds, setSeconds] = useState(0)
  const wide = useMediaQuery(LG)

  useEffect(() => {
    const timer = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  const message =
    seconds < 5
      ? 'Fetching the transcript and picking the most useful parts of the lecture...'
      : 'Waiting for Gemini. Busy periods trigger an automatic retry, so this can take up to a minute.'

  return (
    <div className="fade-in space-y-4">
      <div role="status" aria-live="polite" className={`${CARD} flex flex-wrap items-center justify-between gap-4 p-5`}>
        <div className="min-w-0">
          <p className="font-semibold text-slate-900 dark:text-white">
            Summarising...{' '}
            <span className="font-normal tabular-nums text-slate-600 dark:text-slate-400">{seconds} s</span>
          </p>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{message}</p>
        </div>
        <button type="button" onClick={onCancel} className={BTN_SECONDARY}>
          <XIcon className="h-4 w-4" />
          Cancel
        </button>
      </div>

      {/* The skeleton is decoration: screen readers get the status message above instead.
          It uses the same one-column / two-column arrangement as the real result. */}
      <div aria-hidden="true" className="space-y-4">
        <div className={`${CARD} overflow-hidden`}>
          <div className="flex flex-col sm:flex-row">
            <div className="aspect-video bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800 sm:w-64 sm:shrink-0 lg:w-72" />
            <div className="flex-1 space-y-4 p-5 sm:p-6">
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 space-y-3">
                  <Bar className="w-24" />
                  <div className="h-6 w-2/3 rounded-full bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
                </div>
                <div className="hidden gap-2 lg:flex">
                  <div className="h-9 w-36 rounded-lg bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
                  <div className="h-9 w-32 rounded-lg bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800" />
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Bar className="!h-6 w-28" />
                <Bar className="!h-6 w-36" />
                <Bar className="!h-6 w-24" />
              </div>
            </div>
          </div>
        </div>

        {wide ? (
          <div className="grid grid-cols-12 items-stretch gap-4">
            <div className="col-span-5 flex flex-col gap-4">
              {problem}
              {steps}
              {edgeCases}
            </div>
            <div className="col-span-7 flex flex-col gap-4">
              {code}
              {tiles}
              {revision}
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {problem}
            {steps}
            {code}
            {tiles}
            {edgeCases}
            {revision}
          </div>
        )}
      </div>
    </div>
  )
}
