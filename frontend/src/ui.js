// Shared Tailwind class strings, so cards and buttons look the same everywhere (light and dark).
// Colours: slate neutrals, one indigo accent. Dark mode uses slate-950 pages and slate-900 cards.

export const CARD =
  'rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900'

const BUTTON =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50'

export const BTN_PRIMARY = `${BUTTON} border border-transparent bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 active:bg-indigo-800 dark:hover:bg-indigo-500`

export const BTN_SECONDARY = `${BUTTON} border border-slate-300 bg-white text-slate-700 shadow-sm hover:bg-slate-50 active:bg-slate-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700`

export const BTN_GHOST = `${BUTTON} text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white`
