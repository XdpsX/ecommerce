import { Link } from '@tanstack/react-router'
import { LockKeyhole } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function LoginPage() {
  const { t } = useTranslation('auth')

  return (
    <section className="login-panel" aria-labelledby="login-title">
      <div className="login-icon">
        <LockKeyhole aria-hidden="true" size={22} />
      </div>
      <h1 id="login-title">{t('login.title')}</h1>
      <p>{t('login.description')}</p>
      <button className="button button-disabled" disabled type="button">
        {t('login.unavailable')}
      </button>
      <Link className="text-link" to="/">
        {t('login.back')}
      </Link>
    </section>
  )
}
