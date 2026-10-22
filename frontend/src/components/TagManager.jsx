import { useState } from 'react'
import { Check, Hash, Pencil, Plus, Trash2, X } from 'lucide-react'
import { api } from '../api.js'
import TagChip from './TagChip.jsx'

const COLORS = [
  '#a78bfa', '#f472b6', '#fb923c', '#facc15',
  '#34d399', '#22d3ee', '#60a5fa', '#f87171',
]

export default function TagManager({ tags, onClose, onChanged, onFilterTag }) {
  const [name, setName] = useState('')
  const [color, setColor] = useState(COLORS[0])
  const [renamingId, setRenamingId] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  const [error, setError] = useState('')

  const create = async () => {
    const clean = name.trim().replace(/^#/, '')
    if (!clean) return
    try {
      await api.post('/api/tags/', { name: clean, color })
      setName('')
      setError('')
      onChanged?.()
    } catch (e) {
      setError(e.message)
    }
  }

  const remove = async (tag) => {
    try {
      await api.delete(`/api/tags/${tag.id}/`)
      onChanged?.()
    } catch (e) {
      setError(e.message)
    }
  }

  const saveRename = async (tag) => {
    const clean = renameValue.trim().replace(/^#/, '')
    if (!clean || clean === tag.name) { setRenamingId(null); return }
    try {
      await api.patch(`/api/tags/${tag.id}/`, { name: clean })
      setRenamingId(null)
      setError('')
      onChanged?.()
    } catch (e) {
      setError(e.message)
    }
  }

  const changeColor = async (tag, c) => {
    try {
      await api.patch(`/api/tags/${tag.id}/`, { color: c })
      onChanged?.()
    } catch (e) {
      setError(e.message)
    }
  }

  const inputCls = 'flex-1 px-3 py-2 rounded border border-vault-border bg-vault-surface-2 text-vault-text focus:border-vault-accent focus:outline-none text-sm'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'var(--overlay)' }} onClick={onClose}>
      <div
        className="bg-vault-surface border border-vault-border rounded-xl shadow-2xl w-full max-w-md animate-fade-slide"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-label="Управление тегами"
      >
        <div className="flex items-center gap-2 px-4 py-3 border-b border-vault-border">
          <Hash size={16} className="text-vault-accent" />
          <h2 className="font-semibold text-vault-text flex-1">Все теги</h2>
          <span className="text-xs text-vault-faint">{tags.length}</span>
          <button onClick={onClose} className="text-vault-muted hover:text-vault-text" aria-label="Закрыть">
            <X size={18} />
          </button>
        </div>

        <div className="p-4">
          {/* Создание */}
          <div className="flex gap-2 mb-3">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && create()}
              placeholder="Имя нового тега"
              className={inputCls}
              aria-label="Имя нового тега"
            />
            <div className="flex gap-1.5 items-center px-1">
              {COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => setColor(c)}
                  className={`w-4 h-4 rounded-full transition-transform hover:scale-110 ${color === c ? 'ring-2 ring-vault-accent ring-offset-2 ring-offset-vault-surface' : ''}`}
                  style={{ background: c }}
                  aria-label={`Цвет ${c}`}
                />
              ))}
            </div>
            <button
              onClick={create}
              className="px-3 py-2 rounded bg-vault-accent text-white hover:bg-vault-accent-hover transition-colors"
              aria-label="Создать тег"
              title="Создать тег"
            >
              <Plus size={15} />
            </button>
          </div>

          {error && <div className="mb-3 px-3 py-2 rounded bg-vault-danger/10 text-vault-danger text-sm">{error}</div>}

          {/* Список */}
          <div className="space-y-1 max-h-80 overflow-y-auto pr-1">
            {tags.map((t) => (
              <div key={t.id} className="group flex items-center gap-2 px-2 py-1.5 rounded hover:bg-vault-surface-2 transition-colors">
                {renamingId === t.id ? (
                  <>
                    <input
                      value={renameValue}
                      onChange={(e) => setRenameValue(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') saveRename(t)
                        if (e.key === 'Escape') setRenamingId(null)
                      }}
                      autoFocus
                      className="flex-1 px-2 py-1 rounded border border-vault-accent bg-vault-surface-2 text-sm text-vault-text focus:outline-none"
                      aria-label="Новое имя тега"
                    />
                    <button onClick={() => saveRename(t)} className="p-1 rounded text-vault-success hover:bg-vault-success/10" aria-label="Сохранить имя" title="Сохранить">
                      <Check size={14} />
                    </button>
                    <button onClick={() => setRenamingId(null)} className="p-1 rounded text-vault-muted hover:bg-vault-surface-2" aria-label="Отменить" title="Отменить">
                      <X size={14} />
                    </button>
                  </>
                ) : (
                  <>
                    <TagChip tag={t} onClick={() => { onFilterTag?.(t.name); onClose() }} />
                    <span className="text-xs text-vault-faint shrink-0">{t.note_count ?? 0}</span>
                    <span className="flex-1" />
                    {/* Смена цвета: 4 кружка при hover */}
                    <span className="hidden group-hover:flex gap-1 items-center">
                      {COLORS.slice(0, 4).map((c) => (
                        <button
                          key={c}
                          onClick={() => changeColor(t, c)}
                          className="w-3.5 h-3.5 rounded-full transition-transform hover:scale-125"
                          style={{ background: c }}
                          aria-label={`Перекрасить ${t.name} в ${c}`}
                        />
                      ))}
                    </span>
                    <button
                      onClick={() => { setRenamingId(t.id); setRenameValue(t.name) }}
                      className="text-vault-faint hover:text-vault-accent opacity-0 group-hover:opacity-100 transition-opacity p-1"
                      aria-label="Переименовать тег"
                      title="Переименовать"
                    >
                      <Pencil size={13} />
                    </button>
                    <button
                      onClick={() => remove(t)}
                      className="text-vault-faint hover:text-vault-danger opacity-0 group-hover:opacity-100 transition-opacity p-1"
                      aria-label={`Удалить тег ${t.name}`}
                      title="Удалить"
                    >
                      <Trash2 size={13} />
                    </button>
                  </>
                )}
              </div>
            ))}
            {tags.length === 0 && (
              <div className="text-center py-8">
                <Hash size={22} className="mx-auto text-vault-faint mb-2" />
                <p className="text-sm text-vault-faint">Тегов пока нет — создайте первый</p>
              </div>
            )}
          </div>

          <p className="mt-3 text-[11px] text-vault-faint">
            Клик по тегу — фильтр заметок. Наведите на строку, чтобы сменить цвет, переименовать или удалить.
          </p>
        </div>
      </div>
    </div>
  )
}
