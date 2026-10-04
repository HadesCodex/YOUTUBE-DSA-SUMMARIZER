import { useEffect, useState } from 'react'

// True while the browser window matches a CSS media query, and it updates when the window is resized.
// Used to choose between the stacked and the two-column results layout, so the page structure
// (and therefore the reading order for screen readers) always matches what is drawn.

export const LG = '(min-width: 1024px)' // Tailwind's `lg` breakpoint

export default function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)

  useEffect(() => {
    const list = window.matchMedia(query)
    const update = () => setMatches(list.matches)
    update()
    list.addEventListener('change', update)
    return () => list.removeEventListener('change', update)
  }, [query])

  return matches
}
