import { useCallback, useEffect, useRef, useState } from 'react'
import {
  FileText, FileQuestion, Menu, Moon, Search, Sun, Tags, Trash2, X,
} from 'lucide-react'
import { api, getCookie } from './api.js'
import NoteList from './components/NoteList.jsx'
import NoteEditor from './components/NoteEditor.jsx'
import NewNoteButton from './components/NewNoteButton.jsx'
import TagManager from './components/TagManager.jsx'
import TagChip from './components/TagChip.jsx'
import KeybindsModal from './components/KeybindsModal.jsx'
import SettingsModal from './components/SettingsModal.jsx'
import DonationWidget from './components/DonationWidget.jsx'
import BrandLogo from './components/BrandLogo.jsx'
import BulkBar from './components/BulkBar.jsx'
import ProfileWidget from './components/ProfileWidget.jsx'
import { createNoteSaver } from './noteSaver.js'

const STATUSES = {
  active: { label: 'Активные', icon: FileText },
  draft: { label: 'Черновики', icon: FileQuestion },
  trashed: { label: 'Корзина', icon: Trash2 },
}

export default function App() {
  const [notes, setNotes] = useState([])
  const [selectedId, setSelectedId] = useState(() => Number(location.hash.slice(1)) || null)
  const [status, setStatus] = useState('active')
  const [search, setSearch] = useState('')
  const [tag, setTag] = useState('')
  const [tags, setTags] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saveState, setSaveState] = useState('idle')
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('dragon-theme')
    if (saved) return saved === 'dark'
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  })
  const [mobileListOpen, setMobileListOpen] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const [showTagManager, setShowTagManager] = useState(false)
  const [showKeybinds, setShowKeybinds] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [profileRevision, setProfileRevision] = useState(0)
  const saver = useRef(null)
  const loadVersion = useRef(0)
  if (!saver.current) saver.current = createNoteSaver({
    save: (id, patch) => api.patch(`/api/notes/${id}/`, patch),
    onSaved: (id, saved, pending) => {
      // Child blocks own their interactive state; text saves must not reset them.
      const { todos, images, ...fields } = saved
      setNotes((previous) => previous.map((note) => note.id === id ? { ...note, ...fields, ...pending } : note))
    },
    onError: (error) => setError(error.message),
    onState: setSaveState,
  })
  const searchRef = useRef(null)

  useEffect(() => {
    const warnUnsaved = (event) => {
      if (saver.current.hasPending()) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', warnUnsaved)
    return () => { window.removeEventListener('beforeunload', warnUnsaved); saver.current.dispose() }
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    localStorage.setItem('dragon-theme', dark ? 'dark' : 'light')
  }, [dark])

  const loadNotes = useCallback(async () => {
    const version = ++loadVersion.current
    setLoading(true)
    setError('')
    try {
      const params = new URLSearchParams({ status })
      if (search) params.set('search', search)
      if (tag) params.set('tag', tag)
      const rows = await api.getAll(`/api/notes/?${params}`)
      if (version !== loadVersion.current) return
      setNotes(rows.map((note) => ({ ...note, ...saver.current.pending(note.id) })))
      setSelectedId((id) => rows.some((note) => note.id === id) ? id : null)
    } catch (e) {
      if (version === loadVersion.current) setError(e.message)
    } finally {
      if (version === loadVersion.current) setLoading(false)
    }
  }, [status, search, tag])

  const loadTags = useCallback(async () => {
    try {
      const data = await api.get('/api/tags/dashboard/')
      setTags(data)
    } catch (error) { setError(error.message) }
  }, [])

  useEffect(() => {
    loadNotes()
  }, [loadNotes])

  useEffect(() => {
    loadTags()
  }, [loadTags])

  useEffect(() => { loadTags() }, [loadTags, JSON.stringify(notes.map(n => n.tags))])

  const selected = notes.find((n) => n.id === selectedId) || null

  const createNote = async (type = 'note') => {
    setError('')
    try {
    await saver.current.flushAll()
    const defaults = {
      note: { title: 'Новая заметка', content: '' },
      todo: { title: 'Список задач', content: '' },
    }
    const data = await api.post('/api/notes/', {
      ...defaults[type],
      status: 'active',
      note_type: type,
      tag_names: [],
    })
    setNotes((prev) => [data, ...prev])
    setSelectedId(data.id)
    history.replaceState(null, "", "#"+data.id)
    setMobileListOpen(false)
    } catch (error) { setError(error.message) }
  }

  const updateNote = (id, patch) => {
    setError('')
    setNotes((prev) => prev.map((n) => (n.id === id ? { ...n, ...patch } : n)))
    saver.current.queue(id, patch)
  }

  const changeStatus = async (id, newStatus) => {
    try {
      await saver.current.flush(id)
      const saved = await api.patch(`/api/notes/${id}/`, { status: newStatus })
      setNotes((prev) => prev.filter((n) => n.id !== id))
      if (selectedId === id) setSelectedId(null)
      return saved
    } catch (e) {
      setError(e.message)
    }
  }

  const restoreNote = async (id) => {
    try {
      const saved = await api.post(`/api/notes/${id}/restore/`)
      setNotes((prev) => prev.filter((n) => n.id !== id))
      return saved
    } catch (e) {
      setError(e.message)
    }
  }

  const hardDelete = async (id) => {
    if (!window.confirm('Удалить заметку навсегда?')) return
    try {
      await saver.current.flush(id)
      await api.post(`/api/notes/${id}/hard_delete/`)
      setNotes((prev) => prev.filter((n) => n.id !== id))
      if (selectedId === id) setSelectedId(null)
    } catch (e) {
      setError(e.message)
    }
  }

  const reorder = async (orderedIds) => {
    setNotes((prev) => {
      const map = new Map(prev.map((n) => [n.id, n]))
      return orderedIds.map((id) => map.get(id)).filter(Boolean)
    })
    try {
      await api.post('/api/notes/reorder/', { order: orderedIds })
    } catch (e) {
      setError(e.message)
    }
  }

  const selectNote = (id) => {
    setSelectedId(id)
    history.replaceState(null, "", "#"+id)
    setMobileListOpen(false)
  }

  const toggleSelect = (id) => {
    setSelectedIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id])
  }

  const bulkAction = async (action) => {
    try {
      await saver.current.flushAll()
      await api.post('/api/notes/bulk/', { ids: selectedIds, action })
      setSelectedIds([])
      loadNotes()
    } catch (e) {
      setError(e.message)
    }
  }

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e) => {
      const mod = e.ctrlKey || e.metaKey
      if (!mod) return
      const key = e.key.toLowerCase()
      // Не перехватываем ввод в полях, кроме явно разрешённых комбинаций.
      const tag = e.target?.tagName
      const typing = tag === 'INPUT' || tag === 'TEXTAREA'
      if (key === 'k') {
        e.preventDefault(); searchRef.current?.focus()
      } else if (key === 'e' && !typing) {
        e.preventDefault(); document.querySelector('[title*="Переключить режим"]')?.click()
      } else if (key === 'n' && !typing) {
        e.preventDefault(); createNote('note')
      } else if (key === 'p' && e.shiftKey) {
        // Ctrl+Shift+P — палитра/горячие клавиши. Ctrl+P оставляем браузеру (печать).
        e.preventDefault(); setShowKeybinds(true)
      } else if (key === 's' && !typing) {
        // Ctrl+S: не блокируем, но и не мешаем — автосейв уже идёт. Ничего не делаем.
      } else if (key === 't' && e.shiftKey) {
        e.preventDefault(); createNote('todo')
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  const addTag = (name) => {
    if (!selected || !name) return
    const clean = String(name).trim().replace(/^#/, '')
    if (!clean) return
    const names = saver.current.pending(selected.id).tag_names ?? selected.tags.map((t) => t.name)
    if (!names.includes(clean)) {
      updateNote(selected.id, { tag_names: [...names, clean] })
    }
  }

  const removeTag = (name) => {
    if (!selected) return
    const names = (saver.current.pending(selected.id).tag_names ?? selected.tags.map((t) => t.name)).filter((n) => n !== name)
    updateNote(selected.id, { tag_names: names })
  }

  const logout = async () => {
    try { await saver.current.flushAll() } catch { return }
    // Выход через форму POST — так работают Django logout и CSRF без ошибки 405.
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = '/logout'
    const csrf = document.querySelector('meta[name="_csrf"]')?.content
    if (!csrf) { setError('Не найден токен безопасности. Обновите страницу перед выходом.'); return }
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = '_csrf'
    input.value = csrf
    form.appendChild(input)
    document.body.appendChild(form)
    form.submit()
  }

  const uploadImage = async (file) => {
    if (!selected) return
    try {
    const fd = new FormData()
    fd.append('note', selected.id)
    fd.append('image', file)
    const data = await api.upload('/api/images/', fd)
    const markdown = `\n![${data.alt || 'изображение'}](${data.url})\n`
    updateNote(selected.id, { content: selected.content + markdown })
    } catch (error) { setError(error.message) }
  }

  const saveLabel = {
    idle: '',
    saving: 'Сохранение…',
    saved: 'Сохранено',
    error: 'Ошибка сохранения',
  }[saveState]

  return (
    <div className="flex h-screen overflow-hidden bg-vault-bg text-vault-text font-ui">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-60 flex-col bg-vault-surface border-r border-vault-border">
        <div className="px-4 py-3 border-b border-vault-border flex items-center gap-2">
          <div className="flex-1 min-w-0"><BrandLogo /></div>
          <button
            onClick={() => setDark((d) => !d)}
            className="text-vault-muted hover:text-vault-accent transition-colors"
            title="Переключить тему"
            aria-label="Переключить тему"
          >
            {dark ? <Sun size={16} /> : <Moon size={16} />}
          </button>
        </div>
        <nav className="p-2 flex flex-col gap-0.5">
          {Object.entries(STATUSES).map(([key, meta]) => (
            <button
              key={key}
              onClick={() => setStatus(key)}
              className={`px-3 py-1.5 rounded text-left text-sm flex items-center gap-2 transition-colors ${
                status === key
                  ? 'bg-vault-accent-soft text-vault-accent'
                  : 'text-vault-muted hover:text-vault-text hover:bg-vault-surface-2'
              }`}
            >
              <meta.icon size={14} className={status === key ? 'text-vault-accent' : 'text-vault-faint'} />
              {meta.label}
              <span className={`ml-auto text-xs ${status === key ? 'text-vault-accent/70' : 'text-vault-faint'}`}>
                {key === status ? notes.length : ''}
              </span>
            </button>
          ))}
          <button
            onClick={() => setShowTagManager(true)}
            className="px-3 py-1.5 rounded text-left text-sm text-vault-muted hover:text-vault-text hover:bg-vault-surface-2 flex items-center gap-2"
          >
            <Tags size={14} className="text-vault-faint" /> Все теги
            <span className="ml-auto text-xs text-vault-faint">{tags.length}</span>
          </button>
        </nav>

        {/* Облако тегов: клик — фильтр */}
        <div className="px-3 py-2 border-t border-vault-border overflow-y-auto">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-vault-faint mb-1.5 px-1">
            Теги
          </div>
          {tags.length === 0 && (
            <p className="text-xs text-vault-faint px-1">Тегов пока нет</p>
          )}
          <div className="flex flex-wrap gap-1">
            {tags.map((t) => (
              <TagChip key={t.id} tag={t} active={tag === t.name} onClick={() => setTag(tag === t.name ? '' : t.name)} />
            ))}
          </div>
        </div>
        <div className="p-3 border-t border-vault-border">
          <NewNoteButton onCreate={createNote} />
        </div>
        <div className="mt-auto">
          <DonationWidget />
          <ProfileWidget
            refreshKey={profileRevision}
            onOpenSettings={() => setShowSettings(true)}
            onOpenKeybinds={() => setShowKeybinds(true)}
            onLogout={logout}
          />
        </div>
      </aside>

      {/* Mobile top bar */}
      <div className="md:hidden fixed top-0 inset-x-0 z-20 bg-vault-surface border-b border-vault-border flex items-center justify-between px-4 py-2.5">
        <button onClick={() => setMobileListOpen(true)} className="text-vault-muted" aria-label="Открыть список">
          <Menu size={18} />
        </button>
        <BrandLogo compact />
        <button onClick={() => setDark((d) => !d)} className="text-vault-muted" aria-label="Переключить тему">
          {dark ? <Sun size={18} /> : <Moon size={18} />}
        </button>
      </div>

      {/* Mobile drawer */}
      {mobileListOpen && (
        <div className="md:hidden fixed inset-0 z-30" style={{ background: 'var(--overlay)' }} onClick={() => setMobileListOpen(false)}>
          <div
            className="absolute left-0 top-0 bottom-0 w-72 bg-vault-surface shadow-xl flex flex-col animate-drawer-in"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-label="Список заметок"
          >
            <div className="px-4 py-3 border-b border-vault-border flex items-center gap-2">
              <div className="flex-1 min-w-0"><BrandLogo /></div>
              <button onClick={() => setMobileListOpen(false)} className="text-vault-muted hover:text-vault-text" aria-label="Закрыть">
                <X size={18} />
              </button>
            </div>
            <nav className="p-2 flex flex-col gap-0.5">
              {Object.entries(STATUSES).map(([key, meta]) => (
                <button
                  key={key}
                  onClick={() => { setStatus(key); setMobileListOpen(false) }}
                  className={`px-3 py-2 rounded text-left text-sm flex items-center gap-2 ${
                    status === key
                      ? 'bg-vault-accent-soft text-vault-accent'
                      : 'text-vault-muted hover:bg-vault-surface-2'
                  }`}
                >
                  <meta.icon size={14} className={status === key ? 'text-vault-accent' : 'text-vault-faint'} />
                  {meta.label}
                </button>
              ))}
              <button
                onClick={() => { setMobileListOpen(false); setShowTagManager(true) }}
                className="px-3 py-2 rounded text-left text-sm text-vault-muted hover:bg-vault-surface-2 flex items-center gap-2"
              >
                <Tags size={14} className="text-vault-faint" /> Все теги
              </button>
            </nav>
            {/* Облако тегов в мобильном drawer */}
            <div className="px-4 py-2 border-t border-vault-border">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-vault-faint mb-1.5">
                Теги
              </div>
              <div className="flex flex-wrap gap-1">
                {tags.slice(0, 12).map((t) => (
                  <TagChip
                    key={t.id}
                    tag={t}
                    active={tag === t.name}
                    onClick={() => { setTag(tag === t.name ? '' : t.name); setMobileListOpen(false) }}
                  />
                ))}
                {tags.length === 0 && <p className="text-xs text-vault-faint">Тегов пока нет</p>}
              </div>
            </div>
            <div className="p-3 border-t border-vault-border">
              <NewNoteButton onCreate={(t) => { createNote(t); setMobileListOpen(false) }} />
            </div>
            <div className="flex-1 min-h-0 overflow-y-auto">
              <NoteList notes={notes} selectedId={selectedId} selectedIds={selectedIds}
                onSelect={selectNote} onToggleSelect={toggleSelect} onReorder={reorder}
                onFilterTag={(name) => setTag((value) => value === name ? '' : name)}
                activeTag={tag} status={status} loading={loading} />
            </div>
            <div className="mt-auto">
              <DonationWidget />
              <ProfileWidget
                refreshKey={profileRevision}
                onOpenSettings={() => { setMobileListOpen(false); setShowSettings(true) }}
                onLogout={logout}
              />
            </div>
          </div>
        </div>
      )}

      <main className="flex-1 flex flex-col min-w-0 pt-12 md:pt-0">
        {/* Header: поиск + активный фильтр-тег */}
        <div className="px-4 py-2.5 border-b border-vault-border bg-vault-surface flex gap-2 items-center">
          <div className="relative flex-1 max-w-md">
            <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-vault-faint" />
            <input
              ref={searchRef}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Поиск (Ctrl+K)"
              className="w-full pl-8 pr-8 py-1.5 rounded border border-vault-border bg-vault-surface-2 text-vault-text focus:border-vault-accent focus:outline-none text-sm"
            />
            {(search || tag) && (
              <button
                onClick={() => { setSearch(''); setTag('') }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-vault-faint hover:text-vault-danger transition-colors"
                aria-label="Сбросить фильтры"
                title="Сбросить поиск и тег"
              >
                <X size={13} />
              </button>
            )}
          </div>
          {tag && (
            <TagChip
              tag={tags.find((t) => t.name === tag) || tag}
              active
              onClick={() => setTag('')}
              size="md"
            />
          )}
        </div>

        {error && <div role="alert" className="bg-vault-danger/10 text-vault-danger px-4 py-2 text-sm">
          {error}
          {saveState === 'error' && <button className="ml-3 underline" onClick={() => {
            setError(''); saver.current.flushAll().catch(() => {})
          }}>Повторить сохранение</button>}
        </div>}

        {selectedIds.length > 0 && (
          <BulkBar count={selectedIds.length} onAction={bulkAction} onClear={() => setSelectedIds([])} />
        )}

        <div className="flex-1 flex min-h-0">
          <div className="hidden md:block">
            <NoteList
              notes={notes}
              selectedId={selectedId}
              selectedIds={selectedIds}
              onSelect={selectNote}
              onToggleSelect={toggleSelect}
              onReorder={reorder}
              onFilterTag={(name) => setTag((t) => (t === name ? '' : name))}
              activeTag={tag}
              status={status}
              loading={loading}
            />
          </div>
          <NoteEditor
            note={selected}
            onUpdate={updateNote}
            onChangeStatus={changeStatus}
            onRestore={restoreNote}
            onHardDelete={hardDelete}
            status={status}
            onOpenList={() => setMobileListOpen(true)}
            saveState={saveState}
            onUploadImage={uploadImage}
            tagSuggestions={tags}
            onTagInput={addTag}
            onRemoveTag={removeTag}
            onFilterTag={(name) => setTag((t) => (t === name ? '' : name))}
            activeTag={tag}
          />
        </div>

        {/* Status bar */}
        <div className="px-4 py-1.5 border-t border-vault-border bg-vault-surface flex items-center justify-between text-xs text-vault-faint">
          <div className="flex gap-3">
            <span>{notes.length} заметок</span>
          </div>
          <div className={`flex items-center gap-1.5 ${saveState === 'error' ? 'text-vault-danger' : saveState === 'saved' ? 'text-vault-success' : saveState === 'saving' ? 'text-vault-accent animate-pulse-soft' : ''}`}>
            {saveLabel}
          </div>
        </div>
      </main>

      {showTagManager && (
        <TagManager
          tags={tags}
          onClose={() => setShowTagManager(false)}
          onChanged={() => { loadTags(); loadNotes() }}
          onFilterTag={(name) => setTag((t) => (t === name ? '' : name))}
        />
      )}
      {showKeybinds && <KeybindsModal onClose={() => setShowKeybinds(false)} />}
      {showSettings && <SettingsModal onClose={() => setShowSettings(false)} onProfileUpdate={() => setProfileRevision((value) => value + 1)} />}
    </div>
  )
}
