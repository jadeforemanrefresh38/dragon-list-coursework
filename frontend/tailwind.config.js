/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        vault: {
          bg: 'var(--bg)',
          surface: 'var(--surface)',
          surface2: 'var(--surface-2)',
          border: 'var(--border)',
          text: 'var(--text)',
          muted: 'var(--text-muted)',
          faint: 'var(--text-faint)',
          accent: 'var(--accent)',
          'accent-hover': 'var(--accent-hover)',
          'accent-active': 'var(--accent-active)',
          'accent-soft': 'var(--accent-soft)',
          success: 'var(--success)',
          danger: 'var(--danger)',
          'code-bg': 'var(--code-bg)',
        },
      },
      fontFamily: {
        ui: ['Inter', 'system-ui', 'sans-serif'],
        serif: ['"Iowan Old Style"', 'Palatino', 'Georgia', 'serif'],
        mono: ['"JetBrains Mono"', '"SF Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
}
