export function resolveDonationUrl(value) {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || url.username || url.password) return null
    return url.href
  } catch {
    return null
  }
}

// Публичная настройка времени сборки. Пустая строка скрывает блок.
export const donationUrl = resolveDonationUrl(
  document.querySelector('meta[name="donation-url"]')?.content ?? 'https://pay.cloudtips.ru/p/5a3bdc84',
)
