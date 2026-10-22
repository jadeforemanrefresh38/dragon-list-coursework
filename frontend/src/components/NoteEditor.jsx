import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  Bold, Italic, Heading1, Heading2, Heading3, List, ListOrdered,
  Quote, Code, Link, CheckSquare, Eye, Pencil, Menu, Image as ImageIcon,
  Clock, FileText, Pin, Send, Trash2, Upload,
} from 'lucide-react'
import { relativeTime } from '../utils.js'
import ErrorBoundary from './ErrorBoundary.jsx'
import TodoBlock from './TodoBlock.jsx'
import TagChip from './TagChip.jsx'
import TagInput from './TagInput.jsx'

function wrapSelection(textarea, before, after = '') {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = textarea.value.slice(start, end) || 'текст'
  const next = textarea.value.slice(0, start) + before + selected + after + textarea.value.slice(end)
  return { next, start: start + before.length, end: start + before.length + selected.length }
}

const TOOLBAR = [
  { icon: Bold, before: '**', after: '**', title: 'Жирный' },
  { icon: Italic, before: '*', after: '*', title: 'Курсив' },
  { icon: Heading1, before: '# ', title: 'Заголовок 1' },
  { icon: Heading2, before: '## ', title: 'Заголовок 2' },
  { icon: Heading3, before: '### ', title: 'Заголовок 3' },
  { icon: List, before: '- ', title: 'Список' },
  { icon: ListOrdered, before: '1. ', title: 'Нумерованный список' },
  { icon: Quote, before: '> ', title: 'Цитата' },
  { icon: Code, before: '`', after: '`', title: 'Код' },
  { icon: Link, before: '[', after: '](https://)', title: 'Ссылка' },
  { icon: CheckSquare, before: '- [ ] ', title: 'Чекбокс' },
]

export default function NoteEditor({
  note,
  onUpdate,
  onChangeStatus,
  onRestore,
  onHardDelete,
  status,
  onOpenList,
  saveState,
  onUploadImage,
  onTagInput,
  tagSuggestions = [],
  onRemoveTag,
  onFilterTag,
  activeTag,
}) {
  const [mode, setMode] = useState('edit')
  const [dragOver, setDragOver] = useState(false)
  const textareaRef = useRef(null)

  useEffect(() => {
    setMode('edit')
  }, [note?.id])

  if (!note) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-vault-faint gap-3">
        <FileText size={28} className="text-vault-faint/60" />
        <span>Выберите заметку или создайте новую</span>
        <button onClick={onOpenList} className="md:hidden px-4 py-2 bg-vault-accent text-white rounded flex items-center gap-1.5">
          <Menu size={14} /> Открыть список
        </button>
      </div>
    )
  }

  const applyFormat = (item) => {
    const ta = textareaRef.current
    if (!ta) return
    const { next, start, end } = wrapSelection(ta, item.before, item.after)
    onUpdate(note.id, { content: next })
    requestAnimationFrame(() => {
      ta.focus()
      ta.setSelectionRange(start, end)
    })
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files?.[0]
    if (file && file.type.startsWith('image/')) {
      onUploadImage(file)
    }
  }

  const handleFileSelect = (e) => {
    const file = e.target.files?.[0]
    if (file) onUploadImage(file)
    e.target.value = ''
  }

  return (
    <div className="flex-1 flex flex-col min-w-0 bg-vault-surface relative">
      {/* Header */}
      <div className="px-4 py-3 border-b border-vault-border flex items-center gap-2 flex-wrap">
        <button onClick={onOpenList} className="lg:hidden text-vault-muted hover:text-vault-text" aria-label="Открыть список">
          <Menu size={18} />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            {note.note_type === 'todo' ? (
              <FileText size={15} className="text-vault-faint shrink-0" aria-hidden="true" />
            ) : null}
            <input
              value={note.title}
              onChange={(e) => onUpdate(note.id, { title: e.target.value })}
              placeholder="Без названия"
              className="flex-1 min-w-0 text-xl font-semibold bg-transparent text-vault-text focus:outline-none"
            />
          </div>
          <div className="text-xs text-vault-faint mt-0.5 flex items-center gap-1">
            <Clock size={11} aria-hidden="true" />
            Изменено {relativeTime(note.updated_at)}
          </div>
        </div>
        <div className="flex gap-1.5 items-center">
          <button onClick={() => onUpdate(note.id, { is_pinned: !note.is_pinned })} title={note.is_pinned ? 'Открепить' : 'Закрепить'} className="p-2 text-vault-muted hover:text-vault-accent"><Pin size={15} /></button>
          <button
            onClick={() => setMode(mode === 'edit' ? 'preview' : 'edit')}
            className="px-2.5 py-1.5 rounded border border-vault-border text-vault-muted hover:text-vault-accent hover:border-vault-accent flex items-center gap-1.5 text-sm transition-colors"
            title="Переключить режим (Ctrl+E)"
          >
            {mode === 'edit' ? <Eye size={14} /> : <Pencil size={14} />}
            {mode === 'edit' ? 'Просмотр' : 'Редактировать'}
          </button>
          {status === 'trashed' ? (
            <>
              <button
                onClick={() => onRestore(note.id)}
                className="px-2.5 py-1.5 rounded bg-vault-accent text-white text-sm hover:bg-vault-accent-hover transition-colors flex items-center gap-1.5"
              >
                <Upload size={13} /> Восстановить
              </button>
              <button
                onClick={() => onHardDelete(note.id)}
                className="px-2.5 py-1.5 rounded bg-vault-danger text-white text-sm hover:opacity-90 transition-opacity flex items-center gap-1.5"
              >
                <Trash2 size={13} /> Удалить навсегда
              </button>
            </>
          ) : (
            <>
              <button
                onClick={() => onChangeStatus(note.id, note.status === 'draft' ? 'active' : 'draft')}
                className="px-2.5 py-1.5 rounded border border-vault-border text-vault-muted hover:text-vault-text hover:border-vault-muted text-sm flex items-center gap-1.5 transition-colors"
                title={note.status === 'draft' ? 'Сделать активной' : 'Перевести в черновики'}
              >
                {note.status === 'draft'
                  ? <><Send size={13} /> Опубликовать</>
                  : <><FileText size={13} /> В черновик</>}
              </button>
              <button
                onClick={() => onChangeStatus(note.id, 'trashed')}
                className="px-2.5 py-1.5 rounded border border-vault-border text-vault-danger hover:bg-vault-danger/10 text-sm flex items-center gap-1.5 transition-colors"
                title="Переместить в корзину"
              >
                <Trash2 size={13} /> В корзину
              </button>
            </>
          )}
        </div>
      </div>

      {mode === 'edit' ? (
        <>
          <div className="flex gap-0.5 px-2 py-1.5 border-b border-vault-border bg-vault-surface-2 flex-wrap">
            {TOOLBAR.map((item) => {
              const Icon = item.icon
              return (
                <button
                  key={item.title}
                  title={item.title}
                  onClick={() => applyFormat(item)}
                  className="p-1.5 rounded text-vault-muted hover:text-vault-accent hover:bg-vault-accent-soft transition-colors"
                >
                  <Icon size={15} />
                </button>
              )
            })}
            <span className="w-px bg-vault-border mx-1" />
            <button
              onClick={() => document.getElementById('image-upload').click()}
              className="p-1.5 rounded text-vault-muted hover:text-vault-accent hover:bg-vault-accent-soft transition-colors"
              title="Вставить изображение"
            >
              <ImageIcon size={15} />
            </button>
            <input id="image-upload" type="file" accept="image/*" className="hidden" onChange={handleFileSelect} />
          </div>
          <textarea
            ref={textareaRef}
            value={note.content}
            onChange={(e) => onUpdate(note.id, { content: e.target.value })}
            placeholder="Напишите заметку в Markdown..."
            onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            className="flex-1 p-4 resize-none focus:outline-none font-mono text-sm bg-vault-surface text-vault-text leading-relaxed"
          />
          {dragOver && (
            <div className="absolute inset-0 z-10 bg-vault-accent-soft border-2 border-dashed border-vault-accent flex items-center justify-center pointer-events-none">
              <span className="text-vault-accent font-medium">Перетащите изображение сюда</span>
            </div>
          )}
        </>
      ) : (
        <div className="flex-1 overflow-y-auto px-6 py-4 markdown-body animate-fade-slide">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{note.content}</ReactMarkdown>
        </div>
      )}

      {/* Встроенные блоки доступны в обоих режимах, под текстом заметки */}
      {note.note_type === 'todo' && (
        <div className="px-4 pb-2 border-t border-vault-border">
          <ErrorBoundary name="Список задач">
            <TodoBlock note={note} />
          </ErrorBoundary>
        </div>
      )}


      {/* Теги под содержимым: цветные чипы + ввод с автодополнением */}
      <div className="px-4 py-2 border-t border-vault-border flex items-center gap-1.5 flex-wrap">
        {note.tags.length === 0 && (
          <span className="text-xs text-vault-faint mr-1">Теги:</span>
        )}
        {note.tags.map((t) => (
          <TagChip
            key={t.id}
            tag={t}
            active={activeTag === t.name}
            onClick={() => onFilterTag(t.name)}
            onRemove={() => onRemoveTag(t.name)}
          />
        ))}
        <TagInput suggestions={tagSuggestions} onAdd={onTagInput} />
      </div>
    </div>
  )
}
