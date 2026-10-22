/* Apply the same theme preference as the app before the page is painted. */
(() => {
  let theme
  try { theme = localStorage.getItem('dragon-theme') } catch {}
  document.documentElement.classList.toggle('dark', theme ? theme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.addEventListener('DOMContentLoaded', () => {
    const themeButton = document.querySelector('.auth-theme')
    themeButton.hidden = false
    themeButton.addEventListener('click', () => {
      const dark = document.documentElement.classList.toggle('dark')
      try { localStorage.setItem('dragon-theme', dark ? 'dark' : 'light') } catch {}
    })
    document.querySelectorAll('.auth-password-toggle').forEach((button) => {
      button.hidden = false
      button.addEventListener('click', () => {
        const input = document.getElementById(button.getAttribute('aria-controls'))
        const reveal = input.type === 'password'
        input.type = reveal ? 'text' : 'password'
        button.setAttribute('aria-pressed', String(reveal))
        button.setAttribute('aria-label', reveal ? 'Скрыть пароль' : 'Показать пароль')
      })
    })
  })
})()
