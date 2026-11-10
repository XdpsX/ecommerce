import { AppHeader } from './components/AppHeader'
import { useSession } from '@/features/auth/session'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Outlet } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export function AppShell() {
  const { t } = useTranslation('common')
  const { logoutFailed } = useSession()
  const { t: authT } = useTranslation('auth')

  return (
    <div className="app-shell bg-background text-foreground">
      <a className="skip-link" href="#main-content">
        {t('app.skipContent')}
      </a>
      <AppHeader />
      <main id="main-content" className="main-content" tabIndex={-1}>
        {logoutFailed && (
          <Alert variant="destructive" className="mb-5">
            <AlertDescription>{authT('session.logoutFailed')}</AlertDescription>
          </Alert>
        )}
        <Outlet />
      </main>
      <footer className="app-footer">
        <span>Commerce Backoffice</span>
        <span>{t('app.footerDescription')}</span>
      </footer>
    </div>
  )
}
