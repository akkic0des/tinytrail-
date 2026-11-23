// Set VITE_API_URL at build time (see .env.example); falls back to the local backend.
export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/** Calls the API and turns every failure into an Error with a message that is safe to show users. */
export async function api(path, options) {
  let res
  try {
    res = await fetch(API_URL + path, { headers: { 'Content-Type': 'application/json' }, ...options })
  } catch {
    throw new Error('Cannot reach the server. Please check your connection and try again.')
  }
  if (res.ok) return res.json()

  const body = await res.json().catch(() => ({}))
  if (res.status === 429) {
    const secs = res.headers.get('Retry-After')
    throw new Error(`You're creating links too fast. Please try again in ${secs ?? 'a few'} seconds.`)
  }
  if (res.status === 404) throw new Error('Link not found.')
  // 400 (invalid URL/alias) and 409 (alias taken) carry a ready-to-show message from the backend.
  throw new Error(body.message ?? 'Something went wrong. Please try again.')
}
