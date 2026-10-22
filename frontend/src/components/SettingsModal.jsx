import { useEffect, useRef, useState } from 'react'
import { Camera, KeyRound, Loader2, User, X } from 'lucide-react'
import { ApiError, api } from '../api.js'

/** Разбирает payload ошибки в {поле: текст}. */
function fieldErrors(payload) {
  if (!payload || typeof payload !== 'object') return {}
  const out = {}
  for (const [key, val] of Object.entries(payload)) {
    if (Array.isArray(val)) out[key] = val[0]
    else if (typeof val === 'string') out[key] = val
    else if (val?.detail) out[key] = val.detail
  }
  return out
}

export default function SettingsModal({ onClose, onProfileUpdate }) {
  const [tab, setTab] = useState('profile')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [bio, setBio] = useState('')
  const [avatar, setAvatar] = useState(null)
  const [avatarPreview, setAvatarPreview] = useState(null)
  const [oldPass, setOldPass] = useState('')
  const [newPass, setNewPass] = useState('')
  const [confirmPass, setConfirmPass] = useState('')
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')
  const [fieldErr, setFieldErr] = useState({})
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)
  const [loadingProfile, setLoadingProfile] = useState(true)
  const saveTimer = useRef(null)
  const inFlight = useRef(false)

  useEffect(() => {
    let cancelled = false
    api.get('/api/profile/').then((data) => {
      if (cancelled) return
      setUsername(data.username || '')
      setEmail(data.email || '')
      setBio(data.bio || '')
      setAvatarPreview(data.avatar || null)
    }).catch((error) => { if (!cancelled) setErr(error.message) })
      .finally(() => { if (!cancelled) setLoadingProfile(false) })
    return () => { cancelled = true }
  }, [])

  // Освобождаем blob-URL превью аватара при смене/закрытии (иначе утечка памяти).
  useEffect(() => {
    if (!avatar) return undefined
    const url = URL.createObjectURL(avatar)
    setAvatarPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [avatar])

  const doSaveProfile = async () => {
    if (inFlight.current) return
    inFlight.current = true
    setSavingProfile(true)
    setErr('')
    setMsg('')
    setFieldErr({})
    const fd = new FormData()
    fd.append('username', username)
    fd.append('email', email)
    fd.append('bio', bio)
    if (avatar) fd.append('avatar', avatar)
    try {
      // POST — поддерживаемый метод для /api/profile/save/ (бэкенд принимает POST/PATCH/PUT).
      const data = await api.upload('/api/profile/save/', fd, 'POST')
      setMsg('Профиль сохранён')
      onProfileUpdate?.(data)
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setFieldErr(fieldErrors(e.payload))
      } else {
        setErr(e.message)
      }
    } finally {
      inFlight.current = false
      setSavingProfile(false)
    }
  }

  // Дебаунс: защищает от «залпового» спама запросов (наблюдались до 7 запросов/сек).
  const saveProfile = () => {
    if (saveTimer.current) clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(doSaveProfile, 400)
  }

  useEffect(() => () => {
    if (saveTimer.current) clearTimeout(saveTimer.current)
  }, [])

  const changePassword = async () => {
    if (inFlight.current) return
    setErr('')
    setMsg('')
    setFieldErr({})
    if (newPass !== confirmPass) {
      setFieldErr({ new_password2: 'Пароли не совпадают' })
      return
    }
    inFlight.current = true
    setSavingPassword(true)
    try {
      await api.post('/api/profile/change_password/', {
        old_password: oldPass,
        new_password1: newPass,
        new_password2: confirmPass,
      })
      setMsg('Пароль изменён')
      setOldPass(''); setNewPass(''); setConfirmPass('')
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setFieldErr(fieldErrors(e.payload))
      } else {
        setErr(e.message)
      }
    } finally {
      inFlight.current = false
      setSavingPassword(false)
    }
  }

  const inputClass = (field) =>
    `w-full px-3 py-2 rounded border bg-vault-surface-2 text-vault-text focus:outline-none text-sm ${
      fieldErr[field] ? 'border-vault-danger' : 'border-vault-border focus:border-vault-accent'
    }`

  const FieldError = ({ name }) =>
    fieldErr[name] ? <p className="text-xs text-vault-danger mt-1">{fieldErr[name]}</p> : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'var(--overlay)' }} onClick={onClose}>
      <div
        className="bg-vault-surface border border-vault-border rounded-xl shadow-2xl w-full max-w-lg animate-fade-slide"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-label="Настройки профиля"
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-vault-border">
          <h2 className="font-semibold text-vault-text flex items-center gap-2">
            <User size={16} className="text-vault-accent" /> Настройки
          </h2>
          <button onClick={onClose} className="text-vault-muted hover:text-vault-text" aria-label="Закрыть">
            <X size={18} />
          </button>
        </div>
        <div className="flex border-b border-vault-border">
          <button
            onClick={() => { setTab('profile'); setErr(''); setMsg(''); setFieldErr({}) }}
            className={`flex-1 px-4 py-2.5 text-sm flex items-center justify-center gap-1.5 ${tab === 'profile' ? 'text-vault-accent border-b-2 border-vault-accent' : 'text-vault-muted'}`}
          >
            <User size={14} /> Профиль
          </button>
          <button
            onClick={() => { setTab('security'); setErr(''); setMsg(''); setFieldErr({}) }}
            className={`flex-1 px-4 py-2.5 text-sm flex items-center justify-center gap-1.5 ${tab === 'security' ? 'text-vault-accent border-b-2 border-vault-accent' : 'text-vault-muted'}`}
          >
            <KeyRound size={14} /> Безопасность
          </button>
        </div>

        <div className="p-4">
          {msg && <div className="mb-3 px-3 py-2 rounded bg-vault-success/10 text-vault-success text-sm">{msg}</div>}
          {err && <div className="mb-3 px-3 py-2 rounded bg-vault-danger/10 text-vault-danger text-sm">{err}</div>}

          {tab === 'profile' ? (
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <div className="w-14 h-14 rounded-full bg-vault-surface-2 border border-vault-border flex items-center justify-center overflow-hidden">
                  {avatarPreview ? (
                    <img src={avatarPreview} alt="Аватар" className="w-full h-full object-cover" />
                  ) : (
                    <Camera size={20} className="text-vault-faint" />
                  )}
                </div>
                <label className="px-3 py-1.5 rounded border border-vault-border text-sm text-vault-muted hover:text-vault-accent cursor-pointer">
                  Загрузить аватар
                  <input type="file" accept="image/*" className="hidden" disabled={loadingProfile} onChange={(e) => setAvatar(e.target.files[0] || null)} />
                </label>
              </div>
              <FieldError name="avatar" />
              <div>
                <label className="block text-xs text-vault-muted mb-1">Имя пользователя</label>
                <input value={username} disabled={loadingProfile} onChange={(e) => setUsername(e.target.value)} className={inputClass('username')} />
                <FieldError name="username" />
              </div>
              <div>
                <label className="block text-xs text-vault-muted mb-1">Email</label>
                <input value={email} disabled={loadingProfile} onChange={(e) => setEmail(e.target.value)} className={inputClass('email')} />
                <FieldError name="email" />
              </div>
              <div>
                <label className="block text-xs text-vault-muted mb-1">О себе</label>
                <textarea value={bio} disabled={loadingProfile} onChange={(e) => setBio(e.target.value)} rows={3} className={`${inputClass('bio')} resize-none`} />
                <FieldError name="bio" />
              </div>
              <button
                onClick={saveProfile}
                disabled={savingProfile || loadingProfile}
                className="w-full py-2 rounded bg-vault-accent text-white text-sm hover:bg-vault-accent-hover transition-colors disabled:opacity-60 flex items-center justify-center gap-1.5"
              >
                {savingProfile && <Loader2 size={14} className="animate-spin" />}
                Сохранить профиль
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              <div>
                <label className="block text-xs text-vault-muted mb-1">Текущий пароль</label>
                <input type="password" value={oldPass} onChange={(e) => setOldPass(e.target.value)} className={inputClass('old_password')} />
                <FieldError name="old_password" />
              </div>
              <div>
                <label className="block text-xs text-vault-muted mb-1">Новый пароль</label>
                <input type="password" value={newPass} onChange={(e) => setNewPass(e.target.value)} className={inputClass('new_password1')} />
                <FieldError name="new_password1" />
              </div>
              <div>
                <label className="block text-xs text-vault-muted mb-1">Повторите новый пароль</label>
                <input type="password" value={confirmPass} onChange={(e) => setConfirmPass(e.target.value)} className={inputClass('new_password2')} />
                <FieldError name="new_password2" />
              </div>
              <button
                onClick={changePassword}
                disabled={savingPassword}
                className="w-full py-2 rounded bg-vault-accent text-white text-sm hover:bg-vault-accent-hover transition-colors disabled:opacity-60 flex items-center justify-center gap-1.5"
              >
                {savingPassword && <Loader2 size={14} className="animate-spin" />}
                Изменить пароль
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
