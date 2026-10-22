import { useEffect, useRef, useState } from 'react'
import { ChevronUp, LogOut, Settings } from 'lucide-react'
import { api } from '../api.js'

/** Виджет профиля в сайдбаре: аватар, имя, меню (настройки, выход). */
export default function ProfileWidget({ onOpenSettings, onLogout, refreshKey = 0 }) {
  const [profile, setProfile] = useState(null)
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    let cancelled = false
    api.get('/api/profile/')
      .then((data) => { if (!cancelled) setProfile(data) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [refreshKey])

  // Закрытие меню по клику вне — чтобы не оставалось «залипших» обработчиков.
  useEffect(() => {
    if (!open) return undefined
    const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const username = profile?.username || 'Профиль'
  const initial = username.charAt(0).toUpperCase()

  return (
    <div className="relative px-3 py-3 border-t border-vault-border" ref={ref}>
      {open && (
        <div className="absolute bottom-full mb-1 left-2 right-2 bg-vault-surface border border-vault-border rounded-lg shadow-xl py-1 animate-fade-slide z-20">
          <button
            onClick={() => { onOpenSettings(); setOpen(false) }}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-vault-text hover:bg-vault-surface-2"
          >
            <Settings size={14} className="text-vault-accent" /> Настройки профиля
          </button>
          <div className="h-px bg-vault-border my-1" />
          <button
            onClick={() => { setOpen(false); onLogout() }}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-vault-danger hover:bg-vault-danger/10"
          >
            <LogOut size={14} /> Выйти
          </button>
        </div>
      )}

      <button
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center gap-2.5 p-2 rounded-lg border border-vault-border bg-vault-surface-2 hover:border-vault-accent transition-colors text-left"
        aria-label="Меню профиля"
        aria-expanded={open}
      >
        <span className="w-8 h-8 rounded-full overflow-hidden bg-vault-accent-soft border border-vault-border flex items-center justify-center shrink-0">
          {profile?.avatar ? (
            <img src={profile.avatar} alt="Аватар" className="w-full h-full object-cover" />
          ) : (
            <span className="text-vault-accent text-sm font-semibold">{initial}</span>
          )}
        </span>
        <span className="flex-1 min-w-0">
          <span className="block text-sm font-medium text-vault-text truncate">{username}</span>
          <span className="block text-[11px] text-vault-faint truncate">
            {profile?.email || 'Аккаунт'}
          </span>
        </span>
        <ChevronUp size={14} className={`text-vault-faint transition-transform ${open ? '' : 'rotate-180'}`} />
      </button>
    </div>
  )
}
