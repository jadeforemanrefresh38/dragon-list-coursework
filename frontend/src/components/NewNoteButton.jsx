import { useState } from 'react'
import { ChevronDown, FileText, ListChecks, Plus } from 'lucide-react'

export default function NewNoteButton({ onCreate }) {
  const [open, setOpen] = useState(false)

  const handle = (type) => {
    onCreate(type)
    setOpen(false)
  }

  return (
    <div className="relative">
      <div className="flex">
        <button
          onClick={() => handle('note')}
          className="flex-1 bg-vault-accent hover:bg-vault-accent-hover text-white py-1.5 rounded-l flex items-center justify-center gap-1.5 text-sm transition-colors"
          title="Новая заметка (Ctrl+N)"
        >
          <Plus size={15} /> Новая заметка
        </button>
        <button
          onClick={() => setOpen((o) => !o)}
          className="bg-vault-accent hover:bg-vault-accent-hover text-white py-1.5 px-2 rounded-r border-l border-white/20 transition-colors"
          aria-label="Тип новой заметки"
          title="Выбрать тип"
        >
          <ChevronDown size={14} />
        </button>
      </div>
      {open && (
        <div className="absolute bottom-full mb-1 left-0 right-0 bg-vault-surface border border-vault-border rounded-lg shadow-xl py-1 animate-fade-slide z-20">
          <button onClick={() => handle('note')} className="w-full flex items-center gap-2 px-3 py-2 text-sm text-vault-text hover:bg-vault-surface-2">
            <FileText size={14} className="text-vault-accent" /> Обычная заметка
          </button>
          <button onClick={() => handle('todo')} className="w-full flex items-center gap-2 px-3 py-2 text-sm text-vault-text hover:bg-vault-surface-2">
            <ListChecks size={14} className="text-vault-accent" /> Список задач
          </button>
</div>
      )}
    </div>
  )
}
