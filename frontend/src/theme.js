// Light / dark mode.
// The page follows the system preference until the user presses the toggle; after that their choice is
// remembered (localStorage). The `dark` class on <html> switches every `dark:` style in Tailwind.
// index.html applies the same rule in a tiny inline script BEFORE the first paint, so there is no flash.

export const THEME_KEY = 'theme'

/** saved is 'light', 'dark' or null (never chosen); prefersDark is the system setting. */
export function resolveTheme(saved, prefersDark) {
  if (saved === 'light' || saved === 'dark') return saved
  return prefersDark ? 'dark' : 'light'
}

// localStorage can throw (private mode, blocked storage), so every access is wrapped.
export function readSavedTheme() {
  try {
    const value = localStorage.getItem(THEME_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

export function saveTheme(theme) {
  try {
    localStorage.setItem(THEME_KEY, theme)
  } catch {
    // not remembered; the toggle still works for this visit
  }
}

export function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}
