import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'

export const SUPPORTED_LANGUAGES = ['vi', 'en'] as const
export type Language = (typeof SUPPORTED_LANGUAGES)[number]

// One namespace per file: src/locales/<lang>/<namespace>.json
const files = import.meta.glob<Record<string, unknown>>('./locales/*/*.json', {
  eager: true,
  import: 'default',
})

const resources: Record<string, Record<string, Record<string, unknown>>> = {}
for (const [file, content] of Object.entries(files)) {
  const [, lang, name] = /\.\/locales\/([^/]+)\/([^/]+)\.json$/.exec(file) ?? []
  if (lang && name) (resources[lang] ??= {})[name] = content
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    supportedLngs: SUPPORTED_LANGUAGES,
    fallbackLng: 'vi',
    defaultNS: 'common',
    interpolation: { escapeValue: false },
    detection: { order: ['localStorage', 'navigator'], caches: ['localStorage'] },
  })

function syncDocumentLang(lng: string | undefined): void {
  if (lng) document.documentElement.lang = lng.split('-')[0] ?? lng
}
i18n.on('initialized', () => {
  syncDocumentLang(i18n.resolvedLanguage)
})
i18n.on('languageChanged', syncDocumentLang)

export default i18n
