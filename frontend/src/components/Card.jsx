import { CARD } from '../ui.js'

// A result section: an icon, a heading, then the content. Every section of the summary uses this,
// so spacing and type sizes stay consistent.

export default function Card({ icon: Icon, title, children, className = '' }) {
  return (
    <section className={`${CARD} p-5 sm:p-6 ${className}`}>
      <h3 className="mb-4 flex items-center gap-2.5 text-sm font-semibold text-slate-900 dark:text-white">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          <Icon className="h-4 w-4" />
        </span>
        {title}
      </h3>
      {children}
    </section>
  )
}
