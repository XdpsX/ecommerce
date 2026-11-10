import { useSession } from '@/features/auth/session'
import { Button, buttonVariants } from '@/shared/components/ui/button'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

type PrimaryNavigationProps = { className?: string; onNavigate: () => void }
const navLinkClass = buttonVariants({ variant: 'ghost', className: 'nav-link' })
const activeNavLinkClass = buttonVariants({
  variant: 'ghost',
  className: 'nav-link is-active',
})

export function PrimaryNavigation({
  className,
  onNavigate,
}: PrimaryNavigationProps) {
  const { t } = useTranslation('common')
  const session = useSession()
  const { t: authT } = useTranslation('auth')

  return (
    <nav
      aria-label={t('navigation.label')}
      className={`primary-navigation ${className ?? ''}`}
    >
      <Link
        activeOptions={{ exact: true }}
        activeProps={{ className: activeNavLinkClass }}
        className={navLinkClass}
        onClick={onNavigate}
        to="/"
      >
        {t('navigation.dashboard')}
      </Link>
      {session.status === 'authenticated' || session.logoutPending ? (
        <Button
          type="button"
          className="nav-link"
          variant="ghost"
          disabled={session.logoutPending}
          onClick={() => {
            onNavigate()
            void session.logout()
          }}
        >
          {authT(
            session.logoutPending ? 'session.loggingOut' : 'session.logout',
          )}
        </Button>
      ) : (
        <Link
          activeProps={{ className: activeNavLinkClass }}
          className={navLinkClass}
          onClick={onNavigate}
          to="/login"
        >
          {t('navigation.signIn')}
        </Link>
      )}
    </nav>
  )
}
