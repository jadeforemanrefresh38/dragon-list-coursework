import { Keyboard, X } from 'lucide-react'

const SHORTCUTS = [
  { keys: 'Ctrl+N', desc: 'Новая заметка' },
  { keys: 'Ctrl+Shift+P', desc: 'Список горячих клавиш' },
  { keys: 'Ctrl+K', desc: 'Фокус на поиск' },
  { keys: 'Ctrl+E', desc: 'Переключить режим просмотра' },
  { keys: 'Ctrl+Shift+T', desc: 'Создать список задач' },
  { keys: 'Esc', desc: 'Закрыть модальное окно' },
]

export default function KeybindsModal({ onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'var(--overlay)' }} onClick={onClose}>
      <div
        className="bg-vault-surface border border-vault-border rounded-xl shadow-2xl w-full max-w-md animate-fade-slide"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-vault-border">
          <h2 className="font-semibold text-vault-text flex items-center gap-2">
            <Keyboard size={16} className="text-vault-accent" /> Горячие клавиши
          </h2>
          <button onClick={onClose} className="text-vault-muted hover:text-vault-text" aria-label="Закрыть">
            <X size={18} />
          </button>
        </div>
        <div className="p-4 space-y-2">
          {SHORTCUTS.map((s) => (
            <div key={s.keys} className="flex items-center justify-between py-1.5">
              <span className="text-sm text-vault-text">{s.desc}</span>
              <kbd className="px-2 py-0.5 rounded bg-vault-surface-2 border border-vault-border text-xs text-vault-muted font-mono">
                {s.keys}
              </kbd>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
