import { useState } from 'react'
import { Check, FileText, FileQuestion, Inbox, Pin, Trash2 } from 'lucide-react'
import { relativeTime } from '../utils.js'
import TagChip from './TagChip.jsx'

const STATUS_META = {
  active: { label: 'Активные', icon: FileText },
  draft: { label: 'Черновики', icon: FileQuestion },
  trashed: { label: 'Корзина', icon: Trash2 },
}

export default function NoteList({
  notes, selectedId, selectedIds = [], onSelect, onToggleSelect, onReorder,
  onFilterTag, activeTag, status, loading,
}) {
  const [dragId, setDragId] = useState(null)
  const [overId, setOverId] = useState(null)

  const handleDrop = (targetId) => {
    if (!dragId || dragId === targetId) return
    const ids = notes.map((n) => n.id)
    const from = ids.indexOf(dragId)
    const to = ids.indexOf(targetId)
    ids.splice(from, 1)
    ids.splice(to, 0, dragId)
    onReorder(ids)
    setDragId(null)
    setOverId(null)
  }

  const StatusIcon = STATUS_META[status]?.icon || FileText

  return (
    <div className="w-72 md:w-80 border-r border-vault-border bg-vault-surface overflow-y-auto h-full">
      <div className="px-4 py-3 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-vault-muted">
        <StatusIcon size={13} className="text-vault-faint" />
        {STATUS_META[status]?.label || 'Заметки'}
        <span className="ml-auto font-normal normal-case tracking-normal text-vault-faint">{notes.length}</span>
      </div>

      {loading && (
        <div className="px-4 py-3 flex items-center gap-2 text-vault-faint">
          <Inbox size={14} /> Загрузка…
        </div>
      )}
      {!loading && notes.length === 0 && (
        <div className="px-4 py-8 text-center">
          <FileText size={20} className="mx-auto text-vault-faint mb-2" />
          <p className="text-sm text-vault-faint">
            {activeTag ? `Нет заметок с тегом #${activeTag}` : 'Заметок нет'}
          </p>
        </div>
      )}

      {notes.map((note) => {
        const active = selectedId === note.id
        return (
          <div
            key={note.id}
            draggable={status === 'active'}
            onDragStart={() => setDragId(note.id)}
            onDragOver={(e) => { e.preventDefault(); setOverId(note.id) }}
            onDragLeave={() => setOverId(null)}
            onDrop={() => handleDrop(note.id)}
            onClick={(e) => {
              if (e.ctrlKey || e.metaKey) {
                onToggleSelect(note.id)
              } else {
                onSelect(note.id)
              }
            }}
            className={`relative px-4 py-2.5 cursor-pointer border-b border-vault-border transition-colors ${
              active
                ? 'bg-vault-accent-soft'
                : 'hover:bg-vault-surface-2'
            } ${overId === note.id && dragId !== note.id ? 'border-t-2 border-t-vault-accent' : ''}`}
          >
            {active && (
              <span className="absolute left-0 top-0 bottom-0 w-0.5 bg-vault-accent" />
            )}
            <div className="flex items-center gap-2">
              <button
                onClick={(e) => { e.stopPropagation(); onToggleSelect(note.id) }}
                className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 transition-colors ${
                  selectedIds.includes(note.id)
                    ? 'bg-vault-accent border-vault-accent text-white'
                    : 'border-vault-border hover:border-vault-accent'
                }`}
                aria-label="Выбрать заметку"
              >
                {selectedIds.includes(note.id) && <Check size={12} />}
              </button>
              <div className="flex items-center justify-between gap-2 min-w-0 flex-1">
                <span className={`font-medium truncate ${active ? 'text-vault-accent' : 'text-vault-text'}`}>
                  {note.title || '(без заголовка)'}
                </span>
                {note.is_pinned && <Pin size={12} className="text-vault-faint shrink-0" aria-label="Закреплена" />}
              </div>
            </div>
            <div className="text-xs text-vault-faint mt-0.5">{relativeTime(note.updated_at)}</div>
            {note.tags.length > 0 && (
              <div className="flex gap-1 mt-1.5 flex-wrap">
                {note.tags.map((t) => (
                  <TagChip
                    key={t.id}
                    tag={t}
                    active={activeTag === t.name}
                    onClick={(e) => { e.stopPropagation(); onFilterTag(t.name) }}
                  />
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
