import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

const sections = {
  privacy: ['collection', 'purpose', 'storage', 'sharing', 'rights'],
  terms: ['service', 'fairPlay', 'music', 'availability'],
  dataDeletion: ['request', 'verification', 'effect'],
} as const

const pages = [
  { id: 'privacy', path: '/legal/privacy' },
  { id: 'terms', path: '/legal/terms' },
  { id: 'dataDeletion', path: '/legal/data-deletion' },
] as const

export function LegalPage({ page }: { page: keyof typeof sections }) {
  const { t } = useTranslation('legal')
  return (
    <article className="mx-auto w-full max-w-3xl space-y-10 px-4 py-10 text-foreground lg:py-16">
      <header className="space-y-4">
        <h1 className="font-display text-3xl font-extrabold tracking-tight sm:text-4xl">
          {t(`${page}.title`)}
        </h1>
        <p className="text-sm text-muted-foreground">{t('updated')}</p>
        <p className="text-base leading-relaxed">{t(`${page}.intro`)}</p>
      </header>
      <nav aria-label={t('navigation')} className="flex flex-wrap gap-x-6 gap-y-3">
        {pages.map(({ id, path }) => (
          <Link
            key={id}
            to={path}
            aria-current={id === page ? 'page' : undefined}
            className="rounded-sm underline underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
          >
            {t(`${id}.title`)}
          </Link>
        ))}
      </nav>
      {sections[page].map((id) => (
        <section key={id} aria-labelledby={`legal-${id}`} className="space-y-3">
          <h2 id={`legal-${id}`} className="font-display text-xl font-bold">
            {t(`${page}.${id}.title`)}
          </h2>
          <p className="text-base leading-relaxed">{t(`${page}.${id}.body`)}</p>
        </section>
      ))}
      <section aria-labelledby="legal-contact" className="space-y-3">
        <h2 id="legal-contact" className="font-display text-xl font-bold">
          {t('contact.title')}
        </h2>
        <p className="text-base leading-relaxed">{t('contact.body')}</p>
        <a
          href="mailto:tranvanthai20062@gmail.com"
          className="inline-block max-w-full rounded-sm break-all underline underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
        >
          {t('contact.email')}
        </a>
      </section>
    </article>
  )
}
