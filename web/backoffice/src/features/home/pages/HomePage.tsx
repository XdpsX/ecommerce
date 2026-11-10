import { useTranslation } from 'react-i18next'

export function HomePage() {
  const { t } = useTranslation('home')

  return (
    <section className="dashboard-page" aria-labelledby="dashboard-title">
      <h1 id="dashboard-title">{t('dashboard.title')}</h1>
      <div className="dashboard-welcome">
        <h2>{t('dashboard.greeting')}</h2>
        <p>{t('dashboard.description')}</p>
      </div>
    </section>
  )
}
