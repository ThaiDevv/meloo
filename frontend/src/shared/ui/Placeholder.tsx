import { useTranslation } from 'react-i18next'

interface PlaceholderProps {
  /** i18n namespace of the owning feature; its `title` key is shown. */
  ns: string
  /** Ticket that replaces this placeholder, e.g. FE-SOLO-01. */
  ticket: string
  /** Route this placeholder stands in for. */
  route: string
}

export function Placeholder({ ns, ticket, route }: PlaceholderProps) {
  const { t } = useTranslation([ns, 'common'])
  return (
    <section data-testid="placeholder" className="mx-auto max-w-xl p-6 text-center">
      <h1 className="text-xl font-semibold">{t('title', { ns })}</h1>
      <p className="mt-2 text-muted-foreground">
        {t('placeholder.body', { ns: 'common', ticket })}
      </p>
      <code className="mt-4 inline-block rounded bg-muted px-2 py-1 text-sm">{route}</code>
    </section>
  )
}
