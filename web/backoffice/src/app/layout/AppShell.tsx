import { AppHeader } from './components/AppHeader'
import { Outlet } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export function AppShell() {
  const { t } = useTranslation('common')

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t('app.skipContent')}
      </a>
      <AppHeader />
      <main id="main-content" className="main-content" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="app-footer">
        <span>Commerce Backoffice</span>
        <span>{t('app.footerDescription')}</span>
      </footer>
    </div>
  )
}
