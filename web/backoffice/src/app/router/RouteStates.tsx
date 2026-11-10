import { Button, buttonVariants } from '@/shared/components/ui/button'
import { Link, useRouter } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export function RouteError() {
  const router = useRouter()
  const { t } = useTranslation('common')

  return (
    <section className="state-card" aria-labelledby="route-error-title">
      <h1 id="route-error-title">{t('routeError.title')}</h1>
      <p>{t('routeError.description')}</p>
      <Button onClick={() => void router.invalidate()} type="button">
        {t('routeError.retry')}
      </Button>
    </section>
  )
}

export function NotFound() {
  const { t } = useTranslation('common')

  return (
    <section className="state-card" aria-labelledby="not-found-title">
      <h1 id="not-found-title">
        <span className="state-code">404</span>
        {t('notFound.title')}
      </h1>
      <p>{t('notFound.description')}</p>
      <Link className={buttonVariants()} to="/">
        {t('notFound.back')}
      </Link>
    </section>
  )
}
