import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api.js'

const fmtDate = (iso) => new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' })
const fmtDay = (d) => new Date(d + 'T00:00:00Z').toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })

function Card({ label, children }) {
  return (
    <div className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold">{children}</p>
    </div>
  )
}

export default function Stats() {
  const { code } = useParams()
  const [stats, setStats] = useState(null)
  const [analytics, setAnalytics] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setError('')
    Promise.all([api(`/api/links/${code}/stats`), api(`/api/links/${code}/analytics`)])
      .then(([s, a]) => { setStats(s); setAnalytics(a) })
      .catch((e) => setError(e.message))
  }, [code])

  if (error) return <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-red-700">{error}</p>
  if (!stats || !analytics) return <p className="text-slate-500">Loading…</p>

  const data = analytics.clicksPerDay.map((d) => ({ day: fmtDay(d.date), clicks: d.clicks }))

  return (
    <>
      <Link to="/" className="text-sm text-indigo-600">&larr; Back</Link>
      <h1 className="mt-2 break-all text-2xl font-bold">{stats.shortUrl}</h1>
      <p className="break-all text-sm text-slate-500">{stats.originalUrl}</p>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Card label="Total clicks">{analytics.totalClicks}</Card>
        <Card label="Created">{fmtDate(stats.createdAt)}</Card>
        <Card label="Status">
          {!stats.active ? 'Inactive' : stats.expiresAt ? `Expires ${fmtDate(stats.expiresAt)}` : 'Active'}
        </Card>
      </div>

      <section className="mt-6 rounded-xl bg-white p-4 ring-1 ring-slate-200">
        <h2 className="font-semibold">Clicks, last 7 days</h2>
        <div className="mt-3 h-56 sm:h-64" role="img" aria-label={`Clicks per day: ${data.map((d) => `${d.day} ${d.clicks}`).join(', ')}`}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -20 }}>
              <CartesianGrid stroke="#e2e8f0" vertical={false} />
              <XAxis dataKey="day" interval={0} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
              <Tooltip />
              <Line type="monotone" dataKey="clicks" name="Clicks" stroke="#4f46e5" strokeWidth={2}
                    dot={{ r: 4, fill: '#4f46e5', stroke: '#fff', strokeWidth: 2 }} activeDot={{ r: 6 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="mt-6 rounded-xl bg-white p-4 ring-1 ring-slate-200">
        <h2 className="font-semibold">Top referrers</h2>
        {analytics.topReferrers.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No clicks yet.</p>
        ) : (
          <ul className="mt-2 divide-y divide-slate-100">
            {analytics.topReferrers.map((r) => (
              <li key={r.referrer} className="flex justify-between gap-4 py-2 text-sm">
                <span className="truncate">{r.referrer}</span>
                <span className="font-medium">{r.clicks}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
