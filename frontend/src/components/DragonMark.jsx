export default function DragonMark() {
  return (
    <svg width="24" height="24" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <path d="M7 13 4 5l9 5M23 13l5-8-9 5" fill="currentColor" opacity=".7" />
      <path d="M6 15c0-7 20-7 20 0v6c0 6-20 6-20 0z" fill="currentColor" />
      <path d="m6 16-4-2 2 8 4 1m18-7 4-2-2 8-4 1" fill="currentColor" opacity=".6" />
      <ellipse cx="12" cy="17" rx="2" ry="3" fill="var(--surface)" />
      <ellipse cx="21" cy="17" rx="2" ry="3" fill="var(--surface)" />
      <circle cx="12.5" cy="17" r="1" fill="var(--text)" />
      <circle cx="20.5" cy="17" r="1" fill="var(--text)" />
      <path d="M13 23q3 3 6 0" stroke="var(--surface)" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}
