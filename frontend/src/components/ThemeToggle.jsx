import { useEffect, useState } from 'react'
import { applyTheme, readSavedTheme, resolveTheme, saveTheme } from '../theme.js'
import { MoonIcon, SunIcon } from './Icons.jsx'
import { BTN_GHOST } from '../ui.js'

// Follows the system light/dark setting until the user presses the button; then it remembers their choice.

const systemPrefersDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches

export default function ThemeToggle() {
  const [saved, setSaved] = useState(readSavedTheme) // null = "follow the system"
  const [theme, setTheme] = useState(() => resolveTheme(readSavedTheme(), systemPrefersDark()))

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  // While following the system, react when the system setting changes (for example at sunset).
  useEffect(() => {
    if (saved) return undefined
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setTheme(resolveTheme(null, query.matches))
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [saved])

  function toggle() {
    const next = theme === 'dark' ? 'light' : 'dark'
    setTheme(next)
    setSaved(next)
    saveTheme(next)
  }

  const label = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
  return (
    <button type="button" onClick={toggle} aria-label={label} title={label} className={`${BTN_GHOST} !px-2.5`}>
      {theme === 'dark' ? <SunIcon className="h-5 w-5" /> : <MoonIcon className="h-5 w-5" />}
    </button>
  )
}
