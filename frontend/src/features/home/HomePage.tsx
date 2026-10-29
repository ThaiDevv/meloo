import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { ListeningLadder } from './ListeningLadder'
import { HOME_MODES, type HomeMode } from './modes'

function ModeText({ id }: { id: HomeMode['id'] }) {
  const { t } = useTranslation('home')
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-lg font-semibold">{t(`modes.${id}.title`)}</p>
      <p className="text-sm text-muted-foreground">{t(`modes.${id}.description`)}</p>
    </div>
  )
}

export function HomePage() {
  const { t } = useTranslation('home')
  const [solo, ...others] = HOME_MODES

  return (
    <div className="mx-auto w-full max-w-6xl px-4">
      <section
        aria-labelledby="home-title"
        className="grid gap-12 pt-14 pb-24 lg:grid-cols-12 lg:items-center lg:gap-10 lg:py-28"
      >
        <div className="space-y-8 lg:col-span-7">
          <h1
            id="home-title"
            className="motion-rise font-display text-[clamp(2.75rem,8.5vw,6.5rem)] leading-[0.95] font-extrabold tracking-[-0.035em] text-balance"
          >
            {t('hero.title')}
          </h1>
          <p className="motion-rise max-w-xl text-lg" style={{ animationDelay: '70ms' }}>
            {t('hero.body')}
          </p>
          <div className="motion-rise" style={{ animationDelay: '140ms' }}>
            <Button asChild>
              <Link to="/solo">{t('hero.cta')}</Link>
            </Button>
          </div>
        </div>
        <ListeningLadder className="lg:col-span-5" />
      </section>

      <section aria-labelledby="modes-title" className="pb-20 lg:pb-32">
        <h2
          id="modes-title"
          className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl"
        >
          {t('modes.heading')}
        </h2>
        <div className="mt-8 grid gap-3 lg:grid-cols-12">
          {solo?.available ? (
            <Link
              to={solo.to}
              className="group flex min-h-64 flex-col justify-between gap-10 rounded-2xl bg-primary p-6 text-primary-foreground transition-transform duration-150 active:scale-[0.99] motion-reduce:transition-none lg:col-span-7 lg:p-8"
            >
              <span className="font-display text-4xl font-extrabold tracking-[-0.02em] sm:text-5xl">
                {t('modes.solo.title')}
              </span>
              <span className="flex items-end justify-between gap-6">
                <span className="max-w-sm text-base">{t('modes.solo.description')}</span>
                <ArrowRight
                  aria-hidden="true"
                  className="size-8 shrink-0 transition-transform duration-150 group-hover:translate-x-1 motion-reduce:transition-none motion-reduce:group-hover:translate-x-0"
                />
              </span>
            </Link>
          ) : null}
          <ul className="grid gap-3 lg:col-span-5">
            {others.map((mode) => (
              <li key={mode.id}>
                {mode.available ? (
                  <Link
                    to={mode.to}
                    className="flex items-center justify-between gap-4 rounded-2xl bg-surface px-5 py-5 transition-colors hover:bg-raised motion-reduce:transition-none"
                  >
                    <ModeText id={mode.id} />
                    <ArrowRight aria-hidden="true" className="size-5 shrink-0" />
                  </Link>
                ) : (
                  <div className="flex items-center justify-between gap-4 rounded-2xl bg-surface px-5 py-5">
                    <ModeText id={mode.id} />
                    <Badge>{t('modes.soon')}</Badge>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  )
}
