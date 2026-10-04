import { useState } from 'react'
import { exportPdf } from '../pdf.js'
import { BTN_PRIMARY } from '../ui.js'
import { DownloadIcon } from './Icons.jsx'

export default function ExportButton({ result, className = '' }) {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  async function handleClick() {
    setBusy(true)
    setFailed(false)
    try {
      await exportPdf(result)
    } catch (error) {
      console.error('PDF export failed', error)
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={`relative ${className}`}>
      <button type="button" onClick={handleClick} disabled={busy} className={`${BTN_PRIMARY} w-full ${busy ? '!cursor-wait' : ''}`}>
        <DownloadIcon className="h-4 w-4" />
        {busy ? 'Preparing PDF...' : 'Download PDF'}
      </button>
      {failed && (
        <p
          role="alert"
          className="absolute right-0 top-full z-10 mt-2 whitespace-nowrap rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-xs text-red-800 shadow-sm dark:border-red-500/30 dark:bg-red-950 dark:text-red-200"
        >
          The PDF could not be created. Try again.
        </p>
      )}
    </div>
  )
}
