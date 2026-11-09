import i18n from '@/app/i18n'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/components/ui/select'
import { useTranslation } from 'react-i18next'

export function LanguageSwitcher() {
  const { t } = useTranslation('common')
  const language = i18n.resolvedLanguage?.startsWith('vi') ? 'vi' : 'en'

  return (
    <>
      <span className="language-label" id="language-label">
        {t('navigation.language')}
      </span>
      <Select
        onValueChange={(value) => {
          if (value) void i18n.changeLanguage(value)
        }}
        value={language}
      >
        <SelectTrigger
          aria-labelledby="language-label"
          className="language-select"
          id="language-select"
        >
          <SelectValue>
            {t(
              language === 'vi'
                ? 'navigation.vietnamese'
                : 'navigation.english',
            )}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="en">{t('navigation.english')}</SelectItem>
          <SelectItem value="vi">{t('navigation.vietnamese')}</SelectItem>
        </SelectContent>
      </Select>
    </>
  )
}
