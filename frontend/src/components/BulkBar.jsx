import { Archive, FileText, Trash2, X } from 'lucide-react'

export default function BulkBar({ count, onAction, onClear }) {
  return (
    <div className="px-4 py-2 border-b border-vault-border bg-vault-accent-soft flex items-center gap-2 animate-fade-slide">
      <span className="text-sm text-vault-accent font-medium">Выбрано: {count}</span>
      <div className="flex-1" />
      <button onClick={() => onAction('draft')} className="px-2.5 py-1 rounded border border-vault-border text-vault-text text-xs hover:bg-vault-surface-2 flex items-center gap-1" title="В черновик">
        <FileText size={13} /> В черновик
      </button>
      <button onClick={() => onAction('trash')} className="px-2.5 py-1 rounded border border-vault-border text-vault-danger text-xs hover:bg-vault-danger/10 flex items-center gap-1" title="В корзину">
        <Trash2 size={13} /> В корзину
      </button>
      <button onClick={onClear} className="px-2 py-1 rounded text-vault-muted hover:text-vault-text" aria-label="Снять выделение">
        <X size={14} />
      </button>
    </div>
  )
}
