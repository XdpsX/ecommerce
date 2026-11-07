import { LanguageSwitcher } from './LanguageSwitcher'
import { PrimaryNavigation } from './PrimaryNavigation'
import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

export function AppHeader() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { t } = useTranslation('common')

  return (
    <header className="app-header">
      <Link className="brand" to="/" aria-label={t('app.brandLabel')}>
        <span className="brand-mark" aria-hidden="true">
          C
        </span>
        <span>Commerce</span>
        <span className="brand-divider" aria-hidden="true" />
        <span className="brand-section">{t('app.brandArea')}</span>
      </Link>
      <PrimaryNavigation
        menuOpen={menuOpen}
        onNavigate={() => setMenuOpen(false)}
      />
      <div className="header-tools">
        <LanguageSwitcher />
        <button
          aria-controls="primary-navigation"
          aria-expanded={menuOpen}
          aria-label={menuOpen ? t('navigation.close') : t('navigation.open')}
          className="menu-toggle"
          onClick={() => setMenuOpen((open) => !open)}
          type="button"
        >
          <span />
          <span />
        </button>
      </div>
    </header>
  )
}
