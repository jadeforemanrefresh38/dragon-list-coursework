export function getCookie(name, cookies = document.cookie) {
  const prefix = `${name}=`
  const cookie = cookies.split(';').map((value) => value.trim()).find((value) => value.startsWith(prefix))
  if (!cookie) return null
  try { return decodeURIComponent(cookie.slice(prefix.length)) } catch { return null }
}

function errorMessage(payload) {
  if (typeof payload === 'string') return payload
  if (!payload || typeof payload !== 'object') return ''
  if (Array.isArray(payload)) return payload.map(errorMessage).filter(Boolean).join(' ')
  if (payload.detail || payload.message) return errorMessage(payload.detail || payload.message)
  return Object.entries(payload).filter(([key]) => key !== 'code').map(([, value]) => errorMessage(value)).filter(Boolean).join(' ')
}

/** Ошибка API, несущая структурированные данные ответа (для inline-валидации). */
export class ApiError extends Error {
  constructor(status, payload) {
    super(errorMessage(payload) || `Ошибка ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload || {}
  }
}

async function request(path, options = {}) {
  const opts = { ...options }
  opts.headers = { ...(opts.headers || {}) }
  const csrf = document.querySelector('meta[name="_csrf"]')?.content
  if (csrf && !['GET', 'HEAD', 'OPTIONS'].includes((opts.method || 'GET').toUpperCase())) {
    opts.headers[document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'] = csrf
  }
  if (opts.body && typeof opts.body !== 'string' && !(opts.body instanceof FormData)) {
    opts.headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(opts.body)
  }
  const res = await fetch(path, opts)
  let payload = null
  if (res.status !== 204) {
    try { payload = await res.json() } catch { payload = { detail: `Ошибка ${res.status}. Обновите страницу и повторите действие.` } }
  }
  if (res.status === 401 || (res.status === 403 && payload?.code === 'not_authenticated')) {
    window.location.href = '/login'
    throw new ApiError(res.status, { detail: 'Сессия завершилась. Войдите снова.' })
  }
  if (!res.ok) {
    throw new ApiError(res.status, payload)
  }
  if (res.status === 204) return null
  return payload
}

export const api = {
  get: (path) => request(path),
  async getAll(path) {
    const rows = []
    const visited = new Set()
    while (path) {
      const url = new URL(path, window.location.href)
      if (url.origin !== window.location.origin || visited.has(url.href)) throw new ApiError(500, { detail: 'Некорректная ссылка следующей страницы.' })
      visited.add(url.href)
      const data = await request(url.pathname + url.search)
      if (Array.isArray(data)) return rows.concat(data)
      rows.push(...data.results)
      path = data.next
    }
    return rows
  },
  post: (path, body) => request(path, { method: 'POST', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
  upload: (path, formData, method = 'POST') => request(path, { method, body: formData }),
}
