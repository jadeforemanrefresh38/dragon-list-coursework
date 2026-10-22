import { useEffect, useRef, useState } from 'react'
import { Hash, Plus } from 'lucide-react'

/**
 * Поле ввода тега с автодополнением по существующим тегам.
 * suggestions: [{ id, name, color, note_count? }]
 * onAdd(name) вызывается по Enter/клику по подсказке.
 */
export default function TagInput({ suggestions = [], onAdd, placeholder = '+ тег' }) {
  const [value, setValue] = useState('')
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const ref = useRef(null)

  const query = value.trim().toLowerCase().replace(/^#/, '')
  const matches = query
    ? suggestions.filter((t) => t.name.toLowerCase().includes(query)).slice(0, 6)
    : suggestions.slice(0, 6)

  // Закрытие по клику вне
  useEffect(() => {
    if (!open) return undefined
    const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const submit = (name) => {
    const clean = (name ?? value).trim().replace(/^#/, '')
    if (!clean) return
    onAdd(clean)
    setValue('')
    setOpen(false)
    setHighlight(0)
  }

  const onKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      if (open && matches[highlight]) submit(matches[highlight].name)
      else submit(value)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setHighlight((h) => Math.min(h + 1, Math.max(matches.length - 1, 0)))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((h) => Math.max(h - 1, 0))
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="relative" ref={ref}>
      <div className="flex items-center gap-1.5">
        <Hash size={12} className="text-vault-faint shrink-0" aria-hidden="true" />
        <input
          value={value}
          onChange={(e) => { setValue(e.target.value); setOpen(true); setHighlight(0) }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          className="w-24 bg-transparent border-b border-vault-border focus:border-vault-accent focus:outline-none text-xs text-vault-text py-0.5 transition-colors"
          aria-label="Добавить тег"
        />
      </div>
      {open && matches.length > 0 && (
        <div className="absolute bottom-full mb-1 left-0 z-30 min-w-40 bg-vault-surface border border-vault-border rounded-lg shadow-xl py-1 animate-fade-slide">
          {matches.map((t, i) => (
            <button
              key={t.id}
              onClick={() => submit(t.name)}
              onMouseEnter={() => setHighlight(i)}
              className={`w-full flex items-center gap-2 px-3 py-1.5 text-xs text-left transition-colors ${
                i === highlight ? 'bg-vault-accent-soft text-vault-accent' : 'text-vault-text hover:bg-vault-surface-2'
              }`}
            >
              <span className="tag-dot shrink-0" style={{ '--tag-color': t.color || 'var(--accent)' }} aria-hidden="true" />
              #{t.name}
              {t.note_count != null && (
                <span className="ml-auto text-vault-faint">{t.note_count}</span>
              )}
            </button>
          ))}
        </div>
      )}
      {open && query && matches.length === 0 && (
        <div className="absolute bottom-full mb-1 left-0 z-30 min-w-40 bg-vault-surface border border-vault-border rounded-lg shadow-xl py-1.5 px-3 animate-fade-slide">
          <button
            onClick={() => submit(value)}
            className="flex items-center gap-1.5 text-xs text-vault-accent hover:text-vault-accent-hover"
          >
            <Plus size={11} /> Создать «{query}»
          </button>
        </div>
      )}
    </div>
  )
}
