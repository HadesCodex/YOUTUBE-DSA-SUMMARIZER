import { jsPDF } from 'jspdf'
import { describe, expect, it } from 'vitest'
import { sampleResult } from './fixtures.js'
import { NOT_COVERED, buildPdfModel, renderPdf, slugify, toAscii } from './pdf.js'

describe('toAscii (the sanitiser)', () => {
  it('replaces the symbols a model writes in DSA notes', () => {
    expect(toAscii('i \u2264 n and j \u2265 0')).toBe('i <= n and j >= 0')
    expect(toAscii('a \u2192 b \u2190 c')).toBe('a -> b <- c')
    expect(toAscii('n \u00D7 m \u2260 0')).toBe('n x m != 0')
    expect(toAscii('O(n\u00B2) and O(n\u00B3)')).toBe('O(n^2) and O(n^3)')
    expect(toAscii('\u230A(low + high) / 2\u230B')).toBe('floor((low + high) / 2)')
    expect(toAscii('\u0398(n log n)')).toBe('Theta(n log n)')
  })

  it('straightens curly quotes, dashes and ellipses', () => {
    expect(toAscii('\u201Cquoted\u201D \u2018it\u2019s\u2019 a\u2014b\u2013c\u2026')).toBe('"quoted" \'it\'s\' a-b-c...')
  })

  it('drops accents but keeps the letters', () => {
    expect(toAscii('caf\u00E9 na\u00EFve')).toBe('cafe naive')
  })

  it('turns anything it cannot draw into a question mark', () => {
    expect(toAscii('ok \u{1F600} \u0939\u093F')).toBe('ok ? ??')
  })

  it('keeps newlines, normalises CRLF, and expands tabs', () => {
    expect(toAscii('a\r\nb\nc\td')).toBe('a\nb\nc    d')
  })

  it('leaves plain ASCII untouched', () => {
    const plain = 'mid = low + (high - low) / 2; // O(log n)'
    expect(toAscii(plain)).toBe(plain)
  })

  it('copes with null, undefined and non-strings', () => {
    expect(toAscii(null)).toBe('')
    expect(toAscii(undefined)).toBe('')
    expect(toAscii(42)).toBe('42')
  })

  it('output contains only characters the built-in fonts can draw', () => {
    const nasty = 'a\u2264b \u{1F600} \u0939 \u00E9 \u2211 \u221E \u201C \u2026 \u00A0 \t'
    expect(toAscii(nasty)).toMatch(/^[\n\x20-\x7E]*$/)
  })
})

describe('slugify (file name)', () => {
  it('makes a safe lowercase name', () => {
    expect(slugify('Binary Search (Iterative)')).toBe('binary-search-iterative')
    expect(slugify('  Two Pointers / Sliding Window!  ')).toBe('two-pointers-sliding-window')
  })

  it('falls back to "lecture" when nothing usable is left', () => {
    expect(slugify('')).toBe('lecture')
    expect(slugify('\u0939\u093F')).toBe('lecture')
  })

  it('is capped in length without a trailing dash', () => {
    const slug = slugify('a '.repeat(100))
    expect(slug.length).toBeLessThanOrEqual(60)
    expect(slug.endsWith('-')).toBe(false)
  })
})

describe('buildPdfModel', () => {
  const now = new Date('2026-10-04T10:00:00Z')

  it('has the sections in a sensible order', () => {
    const model = buildPdfModel(sampleResult(), { now })
    const headings = model.blocks.filter((b) => b.type === 'heading').map((b) => b.text)
    expect(headings).toEqual([
      'Problem', 'Algorithm steps', 'Pseudocode', 'Time complexity', 'Space complexity', 'Edge cases', 'Revision summary',
    ])
  })

  it('names the file after the topic and describes the source and filtering', () => {
    const model = buildPdfModel(sampleResult(), { now })
    expect(model.filename).toBe('binary-search-notes.pdf')
    expect(model.title).toBe('Binary Search')
    expect(model.meta).toContain('Video: https://www.youtube.com/watch?v=MHf6awe89xw')
    expect(model.meta).toContain('Summarised by gemini-3.8-flash')
    expect(model.meta).toContain('Sent 5025 of 6325 transcript words to the model')
    expect(model.meta).toContain('Generated 2026-10-04')
  })

  it('sanitises every piece of text', () => {
    const result = sampleResult()
    result.summary.topic = 'Binary Search \u2192 \u03B1'
    result.summary.algorithmSteps = ['Check i \u2264 n']
    result.summary.pseudocode = 'if x \u2265 y:\n\treturn \u2205'
    const model = buildPdfModel(result, { now })

    const all = JSON.stringify(model)
    expect(all).not.toMatch(/[^\x09\x0A\x20-\x7E]/)
    expect(model.title).toBe('Binary Search -> alpha')
    expect(model.blocks.find((b) => b.type === 'numbered').items).toEqual(['Check i <= n'])
    expect(model.blocks.find((b) => b.type === 'code').text).toBe('if x >= y:\n    return empty set')
  })

  it('says "not covered" once for a complexity the lecture did not give', () => {
    const model = buildPdfModel(sampleResult(), { now })
    const index = model.blocks.findIndex((b) => b.text === 'Space complexity')
    expect(model.blocks[index + 1]).toEqual({ type: 'paragraph', text: NOT_COVERED })
  })

  it('joins a complexity with its explanation', () => {
    const model = buildPdfModel(sampleResult(), { now })
    const index = model.blocks.findIndex((b) => b.text === 'Time complexity')
    expect(model.blocks[index + 1].text).toBe('O(log n) - The range halves each step.')
  })

  it('shows "not covered" for empty lists and a missing pseudocode', () => {
    const result = sampleResult()
    result.summary.algorithmSteps = []
    result.summary.edgeCases = []
    result.summary.pseudocode = ''
    const model = buildPdfModel(result, { now })
    const paragraphsAfter = (heading) => model.blocks[model.blocks.findIndex((b) => b.text === heading) + 1]
    expect(paragraphsAfter('Algorithm steps')).toEqual({ type: 'paragraph', text: NOT_COVERED })
    expect(paragraphsAfter('Edge cases')).toEqual({ type: 'paragraph', text: NOT_COVERED })
    expect(paragraphsAfter('Pseudocode')).toEqual({ type: 'paragraph', text: NOT_COVERED })
  })

  it('ends with the AI disclaimer', () => {
    const model = buildPdfModel(sampleResult(), { now })
    expect(model.blocks.at(-1).type).toBe('note')
    expect(model.blocks.at(-1).text).toMatch(/AI-generated/)
  })

  it('does not crash on an almost empty response', () => {
    const model = buildPdfModel({ summary: {} }, { now })
    expect(model.filename).toBe('dsa-lecture-summary-notes.pdf')
    expect(model.blocks.length).toBeGreaterThan(5)
  })
})

describe('renderPdf (real jsPDF, no network)', () => {
  const now = new Date('2026-10-04T10:00:00Z')
  const pdfHeader = (doc) => new TextDecoder().decode(new Uint8Array(doc.output('arraybuffer')).slice(0, 5))

  it('produces a valid one-page PDF for a normal summary', () => {
    const doc = renderPdf(new jsPDF({ unit: 'mm', format: 'a4' }), buildPdfModel(sampleResult(), { now }))
    expect(pdfHeader(doc)).toBe('%PDF-')
    expect(doc.getNumberOfPages()).toBe(1)
  })

  it('adds pages for a long summary and never throws on symbols', () => {
    const result = sampleResult()
    result.summary.algorithmSteps = Array.from({ length: 60 }, (_, i) => `Step ${i + 1}: check whether i \u2264 n and move \u2192 the pointer in the array carefully.`)
    result.summary.pseudocode = Array.from({ length: 80 }, (_, i) => `    line_${i} = some_function(arg_${i}) + another_function(arg_${i + 1}) // a comment`).join('\n')

    const doc = renderPdf(new jsPDF({ unit: 'mm', format: 'a4' }), buildPdfModel(result, { now }))

    expect(pdfHeader(doc)).toBe('%PDF-')
    expect(doc.getNumberOfPages()).toBeGreaterThan(2)
  })

  it('handles a very long unbroken line without crashing', () => {
    const result = sampleResult()
    result.summary.pseudocode = 'x'.repeat(500)
    const doc = renderPdf(new jsPDF({ unit: 'mm', format: 'a4' }), buildPdfModel(result, { now }))
    expect(doc.getNumberOfPages()).toBeGreaterThanOrEqual(1)
  })
})
