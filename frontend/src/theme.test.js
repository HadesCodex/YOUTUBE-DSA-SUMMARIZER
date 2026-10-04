import { afterEach, describe, expect, it, vi } from 'vitest'
import { THEME_KEY, readSavedTheme, resolveTheme, saveTheme } from './theme.js'

describe('resolveTheme', () => {
  it('follows the system until the user has chosen', () => {
    expect(resolveTheme(null, true)).toBe('dark')
    expect(resolveTheme(null, false)).toBe('light')
  })

  it('a saved choice wins over the system setting', () => {
    expect(resolveTheme('light', true)).toBe('light')
    expect(resolveTheme('dark', false)).toBe('dark')
  })

  it('ignores a corrupted saved value', () => {
    expect(resolveTheme('purple', true)).toBe('dark')
    expect(resolveTheme('', false)).toBe('light')
  })
})

describe('saved theme (localStorage can fail, so it must never throw)', () => {
  afterEach(() => vi.unstubAllGlobals())

  function fakeStorage(initial = {}) {
    const data = { ...initial }
    return {
      getItem: (k) => (k in data ? data[k] : null),
      setItem: (k, v) => {
        data[k] = String(v)
      },
      data,
    }
  }

  it('saves and reads back light and dark', () => {
    vi.stubGlobal('localStorage', fakeStorage())
    saveTheme('dark')
    expect(readSavedTheme()).toBe('dark')
    saveTheme('light')
    expect(readSavedTheme()).toBe('light')
  })

  it('returns null when nothing valid is saved', () => {
    vi.stubGlobal('localStorage', fakeStorage())
    expect(readSavedTheme()).toBeNull()
    vi.stubGlobal('localStorage', fakeStorage({ [THEME_KEY]: 'blue' }))
    expect(readSavedTheme()).toBeNull()
  })

  it('does not throw when storage is blocked', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('blocked')
      },
    })
    expect(readSavedTheme()).toBeNull()
    expect(() => saveTheme('dark')).not.toThrow()
  })
})
