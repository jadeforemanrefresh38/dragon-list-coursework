import { useEffect, useRef, useState } from 'react'
import { Check, ListChecks, Plus, Trash2 } from 'lucide-react'
import { api } from '../api.js'

/** Встроенный в заметку список задач: создание, отметка, правка, удаление, прогресс. */
export default function TodoBlock({ note }) {
  const [items, setItems] = useState(note.todos || [])
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const inputRef = useRef(null)

  useEffect(() => {
    setItems(note.todos || [])
  }, [note.id, note.todos])

  const add = async () => {
    const value = text.trim()
    if (!value || busy) return
    setBusy(true)
    setError('')
    try {
      const data = await api.post('/api/todos/', {
        note: note.id,
        text: value,
        done: false,
        position: items.length,
      })
      setItems((prev) => [...prev, data])
      setText('')
      inputRef.current?.focus()
    } catch (error) {
      setError(error.message)
    } finally {
      setBusy(false)
    }
  }

  const toggle = async (item) => {
    setError('')
    const next = items.map((t) => (t.id === item.id ? { ...t, done: !t.done } : t))
    setItems(next)
    try {
      await api.patch(`/api/todos/${item.id}/`, { done: !item.done })
    } catch (error) {
      setError(error.message)
      setItems(items) // откат при ошибке
    }
  }

  const rename = async (item) => {
    setError('')
    const value = prompt('Изменить пункт:', item.text)
    if (value == null || !value.trim() || value === item.text) return
    setItems((prev) => prev.map((t) => (t.id === item.id ? { ...t, text: value } : t)))
    try {
      await api.patch(`/api/todos/${item.id}/`, { text: value })
    } catch (error) {
      setError(error.message)
      setItems(items)
    }
  }

  const remove = async (item) => {
    setError('')
    setItems((prev) => prev.filter((t) => t.id !== item.id))
    try {
      await api.delete(`/api/todos/${item.id}/`)
    } catch (error) {
      setError(error.message)
      setItems(items)
    }
  }

  const doneCount = items.filter((t) => t.done).length
  const progress = items.length ? Math.round((doneCount / items.length) * 100) : 0

  return (
    <div className="mt-4 rounded border border-vault-border bg-vault-surface-2 overflow-hidden">
      <div className="flex items-center gap-2 px-3 py-2 border-b border-vault-border">
        <ListChecks size={14} className="text-vault-accent" />
        <span className="text-sm font-semibold text-vault-text">Список задач</span>
        <span className="ml-auto text-xs text-vault-faint">{doneCount}/{items.length}</span>
      </div>
      {items.length > 0 && (
        <div className="h-1 bg-vault-surface">
          <div className="h-full bg-vault-accent transition-all" style={{ width: `${progress}%` }} />
        </div>
      )}
      <div className="p-2">
        {error && <p role="alert" className="text-vault-danger text-xs p-2">{error}</p>}
        {items.map((item) => (
          <div key={item.id} className="group flex items-center gap-2 px-1.5 py-1 rounded hover:bg-vault-surface">
            <button
              onClick={() => toggle(item)}
              className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 transition-colors ${
                item.done ? 'bg-vault-accent border-vault-accent text-white' : 'border-vault-border hover:border-vault-accent'
              }`}
              aria-label={item.done ? 'Снять отметку' : 'Отметить выполненным'}
            >
              {item.done && <Check size={12} />}
            </button>
            <span
              onClick={() => rename(item)}
              className={`flex-1 text-sm cursor-text ${item.done ? 'line-through text-vault-faint' : 'text-vault-text'}`}
            >
              {item.text}
            </span>
            <button
              onClick={() => remove(item)}
              className="text-vault-faint hover:text-vault-danger opacity-0 group-hover:opacity-100 transition-opacity"
              aria-label="Удалить пункт"
            >
              <Trash2 size={13} />
            </button>
          </div>
        ))}
        <div className="flex items-center gap-2 px-1.5 pt-1">
          <input
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && add()}
            placeholder="Новый пункт…"
            className="flex-1 bg-transparent text-sm text-vault-text placeholder:text-vault-faint focus:outline-none py-1"
          />
          <button
            onClick={add}
            disabled={busy || !text.trim()}
            className="p-1 rounded text-vault-accent hover:bg-vault-accent-soft disabled:opacity-40"
            aria-label="Добавить пункт"
          >
            <Plus size={15} />
          </button>
        </div>
      </div>
    </div>
  )
}
