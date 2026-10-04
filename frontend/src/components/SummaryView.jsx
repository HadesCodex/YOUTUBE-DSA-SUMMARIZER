import { useState } from 'react'
import useMediaQuery, { LG } from '../useMediaQuery.js'
import { CARD, BTN_SECONDARY } from '../ui.js'
import Card from './Card.jsx'
import ExportButton from './ExportButton.jsx'
import {
  AlertTriangleIcon,
  BoxIcon,
  CheckIcon,
  ClockIcon,
  CodeIcon,
  CopyIcon,
  FileTextIcon,
  GaugeIcon,
  ListIcon,
  PlusIcon,
  StarIcon,
} from './Icons.jsx'

// The finished summary. Every piece of model text is rendered as ordinary React text (never as HTML),
// so a transcript that tries to smuggle in markup or scripts cannot do anything here.

const NOT_COVERED = 'Not covered in the provided portion of the lecture'
const isNotCovered = (text) => typeof text === 'string' && text.trim().startsWith('Not covered')
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/
const number = (n) => Number(n ?? 0).toLocaleString('en-US')

/** Muted, italic text: how "this was not in the lecture text" is shown (it is not an error). */
function Muted({ children }) {
  return <span className="italic text-slate-600 dark:text-slate-400">{children}</span>
}

/** Normal text, or muted text when the model says something is not covered. */
function Text({ children }) {
  if (!children || isNotCovered(children)) return <Muted>{children || NOT_COVERED}</Muted>
  return <>{children}</>
}

function Chip({ icon: Icon, tone = 'neutral', title, children }) {
  const tones = {
    neutral: 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300',
    accent: 'border-indigo-200 bg-indigo-50 text-indigo-800 dark:border-indigo-500/30 dark:bg-indigo-500/15 dark:text-indigo-200',
    good: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-200',
  }
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${tones[tone]}`}
    >
      {Icon && <Icon className="h-3.5 w-3.5" />}
      {children}
    </span>
  )
}

function Thumbnail({ videoId }) {
  const [failed, setFailed] = useState(false)
  if (!VIDEO_ID.test(videoId || '') || failed) return null
  return (
    <a
      href={`https://www.youtube.com/watch?v=${videoId}`}
      target="_blank"
      rel="noopener noreferrer"
      title="Open the video on YouTube"
      className="block aspect-video shrink-0 overflow-hidden bg-slate-200 sm:w-64 lg:w-72 dark:bg-slate-800"
    >
      <img
        src={`https://img.youtube.com/vi/${videoId}/hqdefault.jpg`}
        alt="Video thumbnail"
        loading="lazy"
        onError={() => setFailed(true)}
        className="h-full w-full object-cover"
      />
    </a>
  )
}

function HeaderCard({ result, onReset }) {
  const { summary, modelUsed, source, stats, usage, videoId } = result
  const total = stats?.totalWords ?? 0
  const sent = stats?.wordsSent ?? 0
  const removedPct = total > 0 ? Math.round((1 - sent / total) * 100) : 0

  return (
    <section className={`${CARD} overflow-hidden`}>
      <div className="flex flex-col sm:flex-row">
        <Thumbnail videoId={videoId} />
        <div className="flex min-w-0 flex-1 flex-col justify-between gap-5 p-5 sm:p-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between lg:gap-6">
            <div className="min-w-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-indigo-700 dark:text-indigo-300">
                Lecture summary
              </p>
              <h2 className="mt-1 break-words text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl dark:text-white">
                {summary.topic}
              </h2>
            </div>
            <div className="flex shrink-0 gap-2">
              <button
                type="button"
                onClick={onReset}
                aria-label="Summarise another lecture"
                className={`${BTN_SECONDARY} shrink-0`}
              >
                <PlusIcon className="h-4 w-4" />
                <span className="sm:hidden">New</span>
                <span className="hidden sm:inline">Summarise another</span>
              </button>
              <ExportButton result={result} className="flex-1 lg:flex-none" />
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Chip tone="accent" title="The model that wrote this summary">
              {modelUsed}
            </Chip>
            <Chip title="Words of the transcript sent to the model, out of the whole transcript">
              {number(sent)} of {number(total)} words sent
            </Chip>
            {removedPct > 0 && (
              <Chip tone="good" title="Share of the transcript that was filtered out before summarising">
                {removedPct}% filtered out
              </Chip>
            )}
            <Chip title="The transcript is cut into sections of about 650 words; only the most useful are kept">
              {number(stats?.chunksKept)} of {number(stats?.chunksCreated)} sections
            </Chip>
            <Chip title="Where the transcript came from">
              {source === 'pasted' ? 'Pasted transcript' : 'YouTube captions'}
            </Chip>
            <Chip title="Tokens the model read, including the instructions">{number(usage?.inputTokens)} input tokens</Chip>
          </div>
        </div>
      </div>
    </section>
  )
}

function Steps({ items }) {
  if (!items || items.length === 0) {
    return (
      <p>
        <Muted>{NOT_COVERED}</Muted>
      </p>
    )
  }
  return (
    <ol className="space-y-3.5">
      {items.map((item, i) => (
        <li key={i} className="flex gap-3">
          <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-800 dark:bg-indigo-500/20 dark:text-indigo-200">
            {i + 1}
          </span>
          <span className="min-w-0 leading-relaxed">
            <Text>{item}</Text>
          </span>
        </li>
      ))}
    </ol>
  )
}

function CodeBlock({ code }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false) // clipboard blocked: nothing else to do
    }
  }

  if (!code || isNotCovered(code)) {
    return (
      <p>
        <Muted>{code || NOT_COVERED}</Muted>
      </p>
    )
  }
  return (
    <div className="overflow-hidden rounded-xl bg-slate-900 dark:bg-black/50 dark:ring-1 dark:ring-slate-800">
      <div className="flex items-center justify-between border-b border-slate-700/70 px-4 py-2">
        <span className="font-mono text-xs text-slate-400">pseudocode</span>
        <button
          type="button"
          onClick={copy}
          aria-label="Copy pseudocode"
          className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
        >
          {copied ? <CheckIcon className="h-3.5 w-3.5 text-emerald-400" /> : <CopyIcon className="h-3.5 w-3.5" />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre className="overflow-x-auto p-4 font-mono text-sm leading-relaxed text-slate-100">
        <code>{code}</code>
      </pre>
    </div>
  )
}

function ComplexityTile({ icon: Icon, label, complexity }) {
  const value = complexity?.value
  const notCovered = !value || isNotCovered(value)
  return (
    <div
      className={`rounded-xl border p-4 sm:p-5 ${
        notCovered
          ? 'border-dashed border-slate-300 bg-transparent dark:border-slate-700'
          : 'border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/50'
      }`}
    >
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-400">
        <Icon className="h-3.5 w-3.5" />
        {label}
      </p>
      {notCovered ? (
        <p className="mt-3 text-sm leading-relaxed">
          <Muted>{NOT_COVERED}</Muted>
        </p>
      ) : (
        <>
          <p className="mt-2 break-words font-mono text-2xl font-semibold leading-tight text-slate-900 sm:text-3xl dark:text-white">
            {value}
          </p>
          {complexity.explanation && !isNotCovered(complexity.explanation) && (
            <p className="mt-2 text-sm leading-relaxed text-slate-600 dark:text-slate-400">{complexity.explanation}</p>
          )}
        </>
      )}
    </div>
  )
}

function EdgeCases({ items }) {
  if (!items || items.length === 0) {
    return (
      <p>
        <Muted>{NOT_COVERED}</Muted>
      </p>
    )
  }
  return (
    <ul className="space-y-2.5">
      {items.map((item, i) => (
        <li key={i} className="flex gap-3">
          <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-slate-400 dark:bg-slate-500" />
          <span className="min-w-0 leading-relaxed">
            <Text>{item}</Text>
          </span>
        </li>
      ))}
    </ul>
  )
}

function RevisionNotes({ items, className = '' }) {
  return (
    <section
      className={`rounded-2xl border border-indigo-200 bg-gradient-to-br from-indigo-50 to-violet-50 p-5 shadow-sm sm:p-6 dark:border-indigo-500/30 dark:from-indigo-500/10 dark:to-violet-500/10 ${className}`}
    >
      <h3 className="mb-4 flex items-center gap-2.5 text-sm font-semibold text-slate-900 dark:text-white">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-200">
          <StarIcon className="h-4 w-4" />
        </span>
        Revision notes
      </h3>
      {!items || items.length === 0 ? (
        <p>
          <Muted>{NOT_COVERED}</Muted>
        </p>
      ) : (
        <ol className="space-y-3">
          {items.map((item, i) => (
            <li key={i} className="flex gap-3 text-slate-800 dark:text-slate-100">
              <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white dark:bg-indigo-500">
                <CheckIcon className="h-3 w-3" />
              </span>
              <span className="min-w-0 leading-relaxed">
                <Text>{item}</Text>
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

export default function SummaryView({ result, onReset }) {
  const { summary } = result
  const wide = useMediaQuery(LG)

  // `grow` makes the last card of a column stretch, so both columns end at the same height (no ragged gap).
  const grow = wide ? 'flex-1' : ''
  const problem = (
    <Card key="problem" icon={FileTextIcon} title="Problem">
      <p className="leading-relaxed">
        <Text>{summary.problemStatement}</Text>
      </p>
    </Card>
  )
  const steps = (
    <Card key="steps" icon={ListIcon} title="Algorithm steps">
      <Steps items={summary.algorithmSteps} />
    </Card>
  )
  const pseudocode = (
    <Card key="pseudocode" icon={CodeIcon} title="Pseudocode">
      <CodeBlock code={summary.pseudocode} />
    </Card>
  )
  const complexity = (
    <Card key="complexity" icon={GaugeIcon} title="Complexity">
      <div className="grid grid-cols-2 gap-3 sm:gap-4">
        <ComplexityTile icon={ClockIcon} label="Time" complexity={summary.timeComplexity} />
        <ComplexityTile icon={BoxIcon} label="Space" complexity={summary.spaceComplexity} />
      </div>
    </Card>
  )
  const edgeCases = (
    <Card key="edge" icon={AlertTriangleIcon} title="Edge cases" className={grow}>
      <EdgeCases items={summary.edgeCases} />
    </Card>
  )
  const revision = <RevisionNotes key="revision" items={summary.revisionSummary} className={grow} />

  return (
    <div className="fade-in space-y-4">
      <HeaderCard result={result} onReset={onReset} />

      {wide ? (
        // Two columns (about 5/12 and 7/12). `items-stretch` + `flex-1` on each column's last card keeps them level.
        <div className="grid grid-cols-12 items-stretch gap-4">
          <div className="col-span-5 flex min-w-0 flex-col gap-4">
            {problem}
            {steps}
            {edgeCases}
          </div>
          <div className="col-span-7 flex min-w-0 flex-col gap-4">
            {pseudocode}
            {complexity}
            {revision}
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {problem}
          {steps}
          {pseudocode}
          {complexity}
          {edgeCases}
          {revision}
        </div>
      )}

      <p className="px-2 text-center text-xs text-slate-600 dark:text-slate-400">
        AI-generated from an auto-captioned lecture, so it can contain mistakes. Check it against the video.
      </p>
    </div>
  )
}
