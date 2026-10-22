import { X } from 'lucide-react'

/**
 * Единый чип тега для всего приложения.
 * tag: { id, name, color } | string (тогда цвет — акцент).
 * onClick — клик по чипу (фильтрация); onRemove — крестик.
 */
export default function TagChip({ tag, active = false, onClick, onRemove, size = 'sm' }) {
  const name = typeof tag === 'string' ? tag : tag.name
  const color = typeof tag === 'string' ? 'var(--accent)' : (tag.color || 'var(--accent)')
  const interactive = Boolean(onClick)
  const pad = size === 'sm' ? '2px 8px' : '3px 10px'
  const fs = size === 'sm' ? '12px' : '13px'

  return (
    <span
      className="tag-chip"
      data-active={active ? 'true' : 'false'}
      style={{ '--tag-color': color, padding: pad, fontSize: fs }}
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={onClick}
      onKeyDown={(e) => {
        if (interactive && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onClick()
        }
      }}
      aria-pressed={interactive ? active : undefined}
      title={interactive ? `Фильтровать по #${name}` : undefined}
    >
      <span className="tag-dot" aria-hidden="true" />
      #{name}
      {onRemove && (
        <button
          className="tag-remove"
          onClick={(e) => { e.stopPropagation(); onRemove() }}
          aria-label={`Удалить тег ${name}`}
          style={{ display: 'inline-flex', background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
        >
          <X size={11} />
        </button>
      )}
    </span>
  )
}
