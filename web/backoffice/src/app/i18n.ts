import authEn from '@/features/auth/locales/en.json'
import authVi from '@/features/auth/locales/vi.json'
import homeEn from '@/features/home/locales/en.json'
import homeVi from '@/features/home/locales/vi.json'
import commonEn from '@/shared/locales/en/common.json'
import commonVi from '@/shared/locales/vi/common.json'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

export type SupportedLanguage = 'en' | 'vi'

export const LANGUAGE_STORAGE_KEY = 'backoffice.language'

const resources = {
  en: { common: commonEn, auth: authEn, home: homeEn },
  vi: { common: commonVi, auth: authVi, home: homeVi },
} as const

export function getPreferredLanguage(
  storage: Pick<Storage, 'getItem'>,
): SupportedLanguage {
  try {
    return storage.getItem(LANGUAGE_STORAGE_KEY) === 'vi' ? 'vi' : 'en'
  } catch {
    return 'en'
  }
}

function readStoredLanguage(): SupportedLanguage {
  try {
    return getPreferredLanguage(window.localStorage)
  } catch {
    return 'en'
  }
}

function persistLanguage(language: string) {
  const supportedLanguage: SupportedLanguage = language === 'vi' ? 'vi' : 'en'

  document.documentElement.lang = supportedLanguage
  document.title = i18n.t('app.documentTitle', {
    lng: supportedLanguage,
    ns: 'common',
  })

  try {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, supportedLanguage)
  } catch {
    // Language switching still works when browser storage is unavailable.
  }
}

i18n.on('languageChanged', persistLanguage)

void i18n.use(initReactI18next).init({
  resources,
  lng: readStoredLanguage(),
  fallbackLng: 'en',
  defaultNS: 'common',
  ns: ['common', 'auth', 'home'],
  interpolation: { escapeValue: false },
})

export default i18n
