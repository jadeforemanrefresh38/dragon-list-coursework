import { useEffect, useRef, useState } from 'react'

/** Возвращает функцию, вызываемую не чаще одного раза в `delay` мс. */
export function useDebouncedCallback(callback, delay = 400) {
  const timer = useRef(null)
  const cbRef = useRef(callback)
  cbRef.current = callback

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])

  return (...args) => {
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => cbRef.current(...args), delay)
  }
}

/** Значение с задержкой (для input-поиска). */
export function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}

/** Перерисовывает компонент раз в `interval` мс (для относительного времени). */
export function useInterval(callback, interval) {
  const cbRef = useRef(callback)
  cbRef.current = callback
  useEffect(() => {
    if (interval == null) return undefined
    const id = setInterval(() => cbRef.current(), interval)
    return () => clearInterval(id)
  }, [interval])
}
