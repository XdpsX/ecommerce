import { useSession } from '@/features/auth/session'
import { Alert, AlertTitle } from '@/shared/components/ui/alert'
import { Button } from '@/shared/components/ui/button'
import { useTranslation } from 'react-i18next'

export function SessionState() {
  const { status, retry } = useSession()
  const { t } = useTranslation('auth')

  return (
    <section className="state-card">
      {status === 'unavailable' && (
        <>
          <Alert
            aria-labelledby="session-unavailable-title"
            variant="destructive"
            className="mb-5"
          >
            <AlertTitle>
              <h1 id="session-unavailable-title" className="m-0 text-base">
                {t('session.unavailable')}
              </h1>
            </AlertTitle>
          </Alert>
          <Button type="button" onClick={retry}>
            {t('session.retry')}
          </Button>
        </>
      )}
      {status !== 'unavailable' && (
        <h1 role="status">{t('session.loading')}</h1>
      )}
    </section>
  )
}
