// PDF export, built in the browser with jsPDF (no server work).
//
// Three layers, so each is easy to explain and test:
//   toAscii()        cleans text, because jsPDF's built-in fonts only cover plain Latin characters
//   buildPdfModel()  turns the API response into a list of simple blocks (heading, paragraph, list, code)
//   renderPdf()      draws those blocks with jsPDF: wraps lines, adds pages, adds page numbers
// exportPdf() loads jsPDF on demand (so it is not part of the first page load) and saves the file.

export const NOT_COVERED = 'Not covered in the provided portion of the lecture'

// Symbols a model may write in DSA notes that the built-in PDF fonts cannot draw.
const REPLACEMENTS = [
  ['≤', '<='], ['≥', '>='], ['≠', '!='], ['≈', '~'],
  ['→', '->'], ['←', '<-'], ['⇒', '=>'], ['↔', '<->'],
  ['×', 'x'], ['÷', '/'], ['−', '-'], ['–', '-'], ['—', '-'],
  ['‘', "'"], ['’', "'"], ['“', '"'], ['”', '"'], ['…', '...'],
  ['•', '*'], ['·', '*'],
  ['∞', 'infinity'], ['√', 'sqrt'],
  ['²', '^2'], ['³', '^3'], ['¹', '^1'], ['ⁿ', '^n'],
  ['Θ', 'Theta'], ['Ω', 'Omega'], ['θ', 'theta'], ['ω', 'omega'],
  ['α', 'alpha'], ['β', 'beta'], ['λ', 'lambda'], ['μ', 'mu'], ['π', 'pi'],
  ['σ', 'sigma'], ['Σ', 'Sum'], ['∑', 'Sum'],
  ['∈', 'in'], ['∉', 'not in'], ['∪', 'union'], ['∩', 'intersection'], ['∅', 'empty set'],
  ['⌊', 'floor('], ['⌋', ')'], ['⌈', 'ceil('], ['⌉', ')'],
  [' ', ' '], ['\t', '    '],
]

/**
 * Makes text safe for jsPDF's built-in fonts: known symbols become ASCII (<=, ->, x ...), accents are
 * dropped (e -> e), and anything else that cannot be drawn becomes "?". Newlines are kept.
 */
export function toAscii(text) {
  let s = String(text ?? '').replace(/\r\n?/g, '\n')
  for (const [from, to] of REPLACEMENTS) s = s.split(from).join(to)
  s = s.normalize('NFKD').replace(/[̀-ͯ]/g, '') // accented letters -> plain letters
  return s.replace(/[^\n\x20-\x7E]/gu, '?') // the u flag counts an emoji as one character, not two
}

/** "Binary Search (Iterative)" -> "binary-search-iterative" (used for the file name). */
export function slugify(text) {
  const slug = toAscii(text)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
    .replace(/-+$/g, '')
  return slug || 'lecture'
}

/**
 * Turns the API response into a plain description of the document.
 * @returns {{filename, title, meta: string[], blocks: Array}} blocks are
 *   {type:'heading'|'paragraph'|'code'|'note', text} or {type:'numbered'|'bullets', items}
 */
export function buildPdfModel(result, { now = new Date() } = {}) {
  const s = result?.summary || {}
  const clean = (value) => toAscii(value ?? '').trim()
  const list = (items) => (Array.isArray(items) ? items.map(clean).filter(Boolean) : [])
  const complexity = (c) => {
    const value = clean(c?.value)
    if (!value || value.startsWith('Not covered')) return NOT_COVERED
    const why = clean(c?.explanation)
    return why ? `${value} - ${why}` : value
  }
  const listBlock = (heading, type, items) => [
    { type: 'heading', text: heading },
    items.length ? { type, items } : { type: 'paragraph', text: NOT_COVERED },
  ]

  const title = clean(s.topic) || 'DSA lecture summary'
  const meta = []
  if (result?.videoId) meta.push(`Video: https://www.youtube.com/watch?v=${toAscii(result.videoId)}`)
  if (result?.modelUsed) meta.push(`Summarised by ${clean(result.modelUsed)}`)
  if (result?.stats) {
    meta.push(`Sent ${result.stats.wordsSent} of ${result.stats.totalWords} transcript words to the model`)
  }
  meta.push(`Generated ${now.toISOString().slice(0, 10)}`)

  const code = clean(s.pseudocode)
  const blocks = [
    { type: 'heading', text: 'Problem' },
    { type: 'paragraph', text: clean(s.problemStatement) || NOT_COVERED },
    ...listBlock('Algorithm steps', 'numbered', list(s.algorithmSteps)),
    { type: 'heading', text: 'Pseudocode' },
    code ? { type: 'code', text: code } : { type: 'paragraph', text: NOT_COVERED },
    { type: 'heading', text: 'Time complexity' },
    { type: 'paragraph', text: complexity(s.timeComplexity) },
    { type: 'heading', text: 'Space complexity' },
    { type: 'paragraph', text: complexity(s.spaceComplexity) },
    ...listBlock('Edge cases', 'bullets', list(s.edgeCases)),
    ...listBlock('Revision summary', 'numbered', list(s.revisionSummary)),
    {
      type: 'note',
      text: 'AI-generated summary of an auto-captioned lecture. It may contain mistakes: check it against the video.',
    },
  ]
  return { filename: `${slugify(title)}-notes.pdf`, title, meta, blocks }
}

// ----- drawing -----

const PAGE_W = 210 // A4, millimetres
const PAGE_H = 297
const MARGIN = 18
const WIDTH = PAGE_W - 2 * MARGIN
const BOTTOM = PAGE_H - MARGIN - 4 // leave room for the footer
const PT_TO_MM = 0.3528
const SLATE = [30, 41, 59]
const GRAY = [100, 116, 139]
const INDIGO = [67, 56, 202]

/** Draws a model (from buildPdfModel) onto a jsPDF document and returns the document. */
export function renderPdf(doc, model) {
  let y = MARGIN
  const lineHeight = (size) => size * PT_TO_MM * 1.4

  function ensureSpace(height) {
    if (y + height > BOTTOM) {
      doc.addPage()
      y = MARGIN
    }
  }

  // Wrapped text; `indent` shifts it right (used by lists).
  function paragraph(text, { size = 10.5, font = 'helvetica', style = 'normal', color = SLATE, indent = 0, after = 2.5 } = {}) {
    doc.setFont(font, style)
    doc.setFontSize(size)
    doc.setTextColor(...color)
    const h = lineHeight(size)
    for (const line of doc.splitTextToSize(text, WIDTH - indent)) {
      ensureSpace(h)
      y += h
      doc.text(line, MARGIN + indent, y)
    }
    y += after
  }

  function list(items, marker) {
    items.forEach((item, i) => {
      const label = marker === 'numbered' ? `${i + 1}.` : '-'
      doc.setFont('helvetica', 'normal')
      doc.setFontSize(10.5)
      doc.setTextColor(...SLATE)
      const h = lineHeight(10.5)
      doc.splitTextToSize(item, WIDTH - 8).forEach((line, lineIndex) => {
        ensureSpace(h)
        y += h
        if (lineIndex === 0) doc.text(label, MARGIN, y)
        doc.text(line, MARGIN + 8, y)
      })
      y += 1.2
    })
    y += 1.5
  }

  // Monospace block with a light background, drawn line by line so it can break across pages.
  function code(text) {
    const size = 9
    const h = lineHeight(size)
    doc.setFont('courier', 'normal')
    doc.setFontSize(size)
    doc.setTextColor(...SLATE)
    y += 1
    for (const raw of text.split('\n')) {
      const pieces = raw === '' ? [''] : doc.splitTextToSize(raw, WIDTH - 6)
      for (const piece of pieces) {
        ensureSpace(h)
        doc.setFillColor(241, 245, 249)
        doc.rect(MARGIN, y, WIDTH, h, 'F')
        y += h
        doc.text(piece, MARGIN + 3, y - h * 0.25)
      }
    }
    y += 3
  }

  paragraph(model.title, { size: 20, style: 'bold', color: [15, 23, 42], after: 1.5 })
  for (const line of model.meta) paragraph(line, { size: 9, color: GRAY, after: 0.4 })
  y += 3

  for (const block of model.blocks) {
    switch (block.type) {
      case 'heading':
        y += 2.5
        ensureSpace(12) // keep a heading together with the first line below it
        paragraph(block.text, { size: 12.5, style: 'bold', color: INDIGO, after: 1.5 })
        break
      case 'paragraph':
        paragraph(block.text)
        break
      case 'numbered':
      case 'bullets':
        list(block.items, block.type)
        break
      case 'code':
        code(block.text)
        break
      case 'note':
        y += 4
        paragraph(block.text, { size: 9, style: 'italic', color: GRAY })
        break
      default:
        break
    }
  }

  // Footer on every page, added last so the total page count is known.
  const pages = doc.getNumberOfPages()
  for (let page = 1; page <= pages; page += 1) {
    doc.setPage(page)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(...GRAY)
    doc.text('DSA Lecture Summariser', MARGIN, PAGE_H - 10)
    doc.text(`Page ${page} of ${pages}`, PAGE_W - MARGIN, PAGE_H - 10, { align: 'right' })
  }
  return doc
}

/** Builds the PDF and downloads it. jsPDF is loaded only when the button is clicked. */
export async function exportPdf(result) {
  const { jsPDF } = await import('jspdf')
  const model = buildPdfModel(result)
  const doc = renderPdf(new jsPDF({ unit: 'mm', format: 'a4' }), model)
  doc.save(model.filename)
  return model.filename
}
