import i18n from '@/app/i18n'
import { useTranslation } from 'react-i18next'

export function LanguageSwitcher() {
  const { t } = useTranslation('common')

  return (
    <>
      <label className="language-label" htmlFor="language-select">
        {t('navigation.language')}
      </label>
      <select
        id="language-select"
        className="language-select"
        onChange={(event) =>
          void i18n.changeLanguage(event.currentTarget.value)
        }
        value={i18n.resolvedLanguage?.startsWith('vi') ? 'vi' : 'en'}
      >
        <option value="en">{t('navigation.english')}</option>
        <option value="vi">{t('navigation.vietnamese')}</option>
      </select>
    </>
  )
}
