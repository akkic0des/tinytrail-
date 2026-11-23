import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes, Link } from 'react-router-dom'
import './index.css'
import Home from './pages/Home.jsx'
import Stats from './pages/Stats.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <header className="mx-auto max-w-2xl px-4 pt-6">
        <Link to="/" className="text-xl font-bold text-indigo-600">TinyTrail</Link>
      </header>
      <main className="mx-auto max-w-2xl px-4 py-6">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/stats/:code" element={<Stats />} />
          <Route path="*" element={<p>Page not found. <Link className="text-indigo-600 underline" to="/">Go home</Link></p>} />
        </Routes>
      </main>
    </BrowserRouter>
  </StrictMode>,
)
