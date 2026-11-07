import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

type PrimaryNavigationProps = {
  menuOpen: boolean
  onNavigate: () => void
}

export function PrimaryNavigation({
  menuOpen,
  onNavigate,
}: PrimaryNavigationProps) {
  const { t } = useTranslation('common')

  return (
    <nav
      aria-label={t('navigation.label')}
      className={menuOpen ? 'primary-navigation is-open' : 'primary-navigation'}
      id="primary-navigation"
    >
      <Link
        activeOptions={{ exact: true }}
        activeProps={{ className: 'nav-link is-active' }}
        className="nav-link"
        onClick={onNavigate}
        to="/"
      >
        {t('navigation.dashboard')}
      </Link>
      <Link
        activeProps={{ className: 'nav-link is-active' }}
        className="nav-link"
        onClick={onNavigate}
        to="/login"
      >
        {t('navigation.signIn')}
      </Link>
    </nav>
  )
}
