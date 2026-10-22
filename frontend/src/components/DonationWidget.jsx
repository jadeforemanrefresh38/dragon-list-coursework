import { ExternalLink, Heart } from 'lucide-react'
import { donationUrl } from '../donation.js'

export default function DonationWidget() {
  if (!donationUrl) return null
  return (
    <section className="px-3 py-3 border-t border-vault-border" aria-label="Поддержка Dragon List">
      <a className="donation-link" href={donationUrl} target="_blank" rel="noopener noreferrer" aria-label="Поддержать проект — CloudTips, новая вкладка">
        <span className="donation-heart"><Heart size={18} aria-hidden="true" /></span>
        <span className="flex-1">Поддержать проект</span>
        <ExternalLink size={14} aria-hidden="true" />
      </a>
    </section>
  )
}
