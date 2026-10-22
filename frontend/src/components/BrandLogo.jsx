import logo from '../assets/dragon-list-logo-v1.png'

/** Branding from psychopussy's shlyapa branch, adapted to the React shell. */
export default function BrandLogo({ compact = false }) {
  return (
    <a href="/" className={`brand-logo${compact ? ' brand-logo--compact' : ''}`} aria-label="Dragon List — главная">
      <img className="brand-logo__image" src={logo} alt="" width="48" height="48" />
      <span className="brand-logo__name">Dragon List</span>
    </a>
  )
}
