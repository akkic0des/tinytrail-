import { useState } from 'react'
import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { api } from '../api.js'

const STORAGE_KEY = 'tinytrail.recent'
const EXPIRY_OPTIONS = [
  { label: 'Never expires', value: '' },
  { label: '1 day', value: '1' },
  { label: '7 days', value: '7' },
  { label: '30 days', value: '30' },
]

function loadRecent() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? []
  } catch {
    return []
  }
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      window.prompt('Copy this link:', text) // clipboard API needs https/localhost
    }
  }
  return (
    <button onClick={copy} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100">
      {copied ? 'Copied!' : 'Copy'}
    </button>
  )
}

export default function Home() {
  const [url, setUrl] = useState('')
  const [alias, setAlias] = useState('')
  const [expiry, setExpiry] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [recent, setRecent] = useState(loadRecent)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const body = { url: url.trim() }
      if (alias.trim()) body.customAlias = alias.trim()
      if (expiry) body.expiresInDays = Number(expiry)
      const link = await api('/api/links', { method: 'POST', body: JSON.stringify(body) })
      setResult(link)
      const next = [link, ...recent.filter((l) => l.shortCode !== link.shortCode)].slice(0, 10)
      setRecent(next)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      } catch {
        /* storage full/blocked: the list just won't persist */
      }
    } catch (err) {
      setResult(null)
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const input = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'

  return (
    <>
      <h1 className="text-3xl font-bold">Shorten a link</h1>
      <p className="mt-1 text-slate-600">Paste a long URL, get a short one you can track.</p>

      <form onSubmit={submit} className="mt-6 space-y-4 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:p-6">
        <div>
          <label htmlFor="url" className="mb-1 block text-sm font-medium">Long URL</label>
          <input id="url" className={input} value={url} onChange={(e) => setUrl(e.target.value)}
                 placeholder="https://example.com/very/long/link" required maxLength={2048} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="alias" className="mb-1 block text-sm font-medium">Custom alias <span className="text-slate-400">(optional)</span></label>
            <input id="alias" className={input} value={alias} onChange={(e) => setAlias(e.target.value)}
                   placeholder="my-link" maxLength={20} />
          </div>
          <div>
            <label htmlFor="expiry" className="mb-1 block text-sm font-medium">Expiry</label>
            <select id="expiry" className={input} value={expiry} onChange={(e) => setExpiry(e.target.value)}>
              {EXPIRY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        <button disabled={loading} className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:opacity-60">
          {loading ? 'Shortening…' : 'Shorten'}
        </button>
      </form>

      {result && (
        <section className="mt-6 flex flex-col items-center gap-4 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:flex-row sm:p-6">
          <QRCodeSVG value={result.shortUrl} size={112} />
          <div className="min-w-0 flex-1 text-center sm:text-left">
            <a href={result.shortUrl} target="_blank" rel="noreferrer" className="block break-all text-lg font-semibold text-indigo-600">{result.shortUrl}</a>
            <p className="mt-1 truncate text-sm text-slate-500">{result.originalUrl}</p>
            <div className="mt-3 flex justify-center gap-2 sm:justify-start">
              <CopyButton text={result.shortUrl} />
              <Link to={`/stats/${result.shortCode}`} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100">Stats</Link>
            </div>
          </div>
        </section>
      )}

      {recent.length > 0 && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold">My recent links</h2>
          <ul className="mt-2 divide-y divide-slate-200 rounded-xl bg-white ring-1 ring-slate-200">
            {recent.map((l) => (
              <li key={l.shortCode} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="truncate font-medium text-indigo-600">{l.shortUrl}</p>
                  <p className="truncate text-sm text-slate-500">{l.originalUrl}</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <CopyButton text={l.shortUrl} />
                  <Link to={`/stats/${l.shortCode}`} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100">Stats</Link>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
