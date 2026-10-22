/** Independent queues per note; in-flight writes for the same note are serialized. */
export function createNoteSaver({ save, onSaved, onError, onState, delay = 600 }) {
  const records = new Map()
  const dirty = (record) => Object.keys(record.pending).length > 0
  const state = () => onState?.([...records.values()].some((r) => r.error) ? 'error'
    : [...records.values()].some((r) => dirty(r) || r.running) ? 'saving' : 'saved')
  const recordFor = (id) => {
    if (!records.has(id)) records.set(id, { pending: {}, inFlight: {}, running: null, timer: null, error: null })
    return records.get(id)
  }
  async function flush(id) {
    const record = recordFor(id)
    clearTimeout(record.timer)
    if (record.running) {
      await record.running
      return flush(id)
    }
    if (!dirty(record)) return
    const patch = record.pending
    record.pending = {}
    record.inFlight = patch
    record.error = null
    record.running = Promise.resolve().then(async () => {
      try {
        const saved = await save(id, patch)
        onSaved?.(id, saved, record.pending)
      } catch (error) {
        record.pending = { ...patch, ...record.pending }
        record.error = error
        onError?.(error)
        throw error
      } finally {
        record.inFlight = {}
        record.running = null
        state()
      }
    })
    state()
    await record.running
    if (dirty(record)) return flush(id)
  }
  return {
    queue(id, patch) {
      const record = recordFor(id)
      record.pending = { ...record.pending, ...patch }
      record.error = null
      clearTimeout(record.timer)
      record.timer = setTimeout(() => flush(id).catch(() => {}), delay)
      state()
    },
    pending(id) {
      const record = records.get(id)
      return record ? { ...record.inFlight, ...record.pending } : {}
    },
    hasPending() { return [...records.values()].some((r) => dirty(r) || r.running) },
    flush,
    flushAll() { return Promise.all([...records.keys()].map(flush)) },
    dispose() { for (const record of records.values()) clearTimeout(record.timer) },
  }
}
