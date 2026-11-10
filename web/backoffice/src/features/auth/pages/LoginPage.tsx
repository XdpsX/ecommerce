import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { buttonVariants } from '@/shared/components/ui/button'
import { getSafeReturnPath } from '@/shared/lib/returnPath'
import { useSearch } from '@tanstack/react-router'
import { LockKeyhole } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function LoginPage() {
  const { t, i18n } = useTranslation('auth')
  const { returnTo, error } = useSearch({ from: '/login' })
  const locale = i18n.resolvedLanguage?.toLowerCase().startsWith('vi')
    ? 'vi'
    : 'en'
  const safeReturnTo = getSafeReturnPath(returnTo)
  const authorizationParameters = new URLSearchParams({ ui_locales: locale })
  if (safeReturnTo !== '/')
    authorizationParameters.set('returnTo', safeReturnTo)

  return (
    <section className="login-panel" aria-labelledby="login-title">
      <div className="login-icon">
        <LockKeyhole aria-hidden="true" size={22} />
      </div>
      <h1 id="login-title">{t('login.title')}</h1>
      <p>{t('login.description')}</p>
      {error && (
        <Alert variant="destructive" className="mb-5">
          <AlertDescription>{t('login.failed')}</AlertDescription>
        </Alert>
      )}
      <a
        aria-label={t('login.action')}
        className={buttonVariants()}
        href={`/oauth2/authorization/identity?${authorizationParameters.toString()}`}
      >
        {t('login.action')}
      </a>
      <p>{t('login.sso')}</p>
    </section>
  )
}
