import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Equalizer, SoundBackdrop, VinylDisc } from '@/shared/music-picker'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { ListeningLadder } from './ListeningLadder'
import { HOME_MODES, type HomeMode } from './modes'
import { Reveal } from './Reveal'
import { WaveBand } from './WaveBand'

const STEPS = ['listen', 'more', 'guess', 'score'] as const
// The stairs go one column further right at every step; on a phone the indent is smaller.
const STEP_PLACEMENT = [
  'lg:col-start-1',
  'ml-[4vw] lg:col-start-2 lg:ml-0',
  'ml-[8vw] lg:col-start-3 lg:ml-0',
  'ml-[12vw] lg:col-start-4 lg:ml-0',
] as const

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
  const { t, i18n } = useTranslation('home')
  const [solo, ...others] = HOME_MODES

  return (
    <>
      <SoundBackdrop />
      <div className="mx-auto w-full max-w-6xl px-4">
        <section
          aria-labelledby="home-title"
          className="grid gap-12 pt-14 pb-24 lg:grid-cols-12 lg:items-center lg:gap-10 lg:py-20"
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
            <div
              className="motion-rise flex items-center gap-5"
              style={{ animationDelay: '140ms' }}
            >
              <Button asChild>
                <Link to="/solo">{t('hero.cta')}</Link>
              </Button>
              <Equalizer className="text-3xl text-primary" />
            </div>
          </div>
          <div className="space-y-8 lg:col-span-5">
            <VinylDisc accent className="mx-auto w-56 text-foreground sm:w-64 lg:w-72" />
            <ListeningLadder />
          </div>
        </section>
      </div>

      <WaveBand />

      <div className="mx-auto w-full max-w-6xl px-4">
        <section aria-labelledby="modes-title" className="py-20 lg:py-32">
          <Reveal>
            <h2
              id="modes-title"
              className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl"
            >
              {t('modes.heading')}
            </h2>
          </Reveal>
          <div className="mt-8 grid gap-3 lg:grid-cols-12">
            {solo?.available ? (
              <Reveal className="lg:col-span-7" delay={80}>
                <Link
                  to={solo.to}
                  className="group relative isolate flex min-h-72 flex-col justify-between gap-10 overflow-hidden rounded-2xl bg-primary p-6 text-primary-foreground transition-transform duration-200 hover:-translate-y-1 active:scale-[0.99] motion-reduce:transition-none motion-reduce:hover:translate-y-0 lg:h-full lg:p-8"
                >
                  <VinylDisc className="pointer-events-none absolute -right-24 -bottom-28 -z-10 w-72 text-primary-foreground opacity-35 sm:-right-20 sm:-bottom-24 sm:w-80 sm:opacity-60 transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100" />
                  <span className="flex items-end gap-4">
                    <span className="font-display text-4xl font-extrabold tracking-[-0.02em] sm:text-5xl">
                      {t('modes.solo.title')}
                    </span>
                    <Equalizer className="mb-1.5 text-3xl" />
                  </span>
                  <span className="flex items-end justify-between gap-6">
                    <span className="max-w-[16rem] text-base">{t('modes.solo.description')}</span>
                    <ArrowRight
                      aria-hidden="true"
                      className="size-8 shrink-0 transition-transform duration-150 group-hover:translate-x-1 motion-reduce:transition-none motion-reduce:group-hover:translate-x-0"
                    />
                  </span>
                </Link>
              </Reveal>
            ) : null}
            <ul className="grid gap-3 lg:col-span-5">
              {others.map((mode, index) => (
                <li key={mode.id}>
                  <Reveal delay={160 + index * 80}>
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
                        <span className="flex shrink-0 items-center gap-3">
                          <Equalizer paused className="text-xl text-muted-foreground opacity-60" />
                          <Badge>{t('modes.soon')}</Badge>
                        </span>
                      </div>
                    )}
                  </Reveal>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section aria-labelledby="how-title" className="pb-24 lg:pb-36">
          <Reveal>
            <h2
              id="how-title"
              className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl"
            >
              {t('how.heading')}
            </h2>
          </Reveal>
          <div className="mt-10 grid gap-10 lg:grid-cols-12 lg:items-stretch">
            <ol className="grid gap-10 lg:col-span-8 lg:grid-cols-8 lg:gap-y-12">
              {STEPS.map((step, index) => (
                <li key={step} className={`lg:col-span-5 ${STEP_PLACEMENT[index] ?? ''}`}>
                  <Reveal delay={index * 70} className="space-y-2">
                    <p
                      className={`font-display text-[clamp(1.5rem,3vw,2.25rem)] leading-tight font-extrabold tracking-[-0.025em] ${index === STEPS.length - 1 ? 'text-primary' : ''}`}
                    >
                      {t(`how.steps.${step}.title`)}
                    </p>
                    <p className="max-w-sm text-base text-muted-foreground">
                      {t(`how.steps.${step}.body`)}
                    </p>
                  </Reveal>
                </li>
              ))}
            </ol>
            <Reveal delay={120} className="lg:col-span-4">
              <div className="flex h-full min-h-64 flex-col justify-between gap-8 rounded-2xl bg-primary p-6 text-primary-foreground lg:p-8">
                <p className="num font-display text-[clamp(4rem,9vw,6.5rem)] leading-none font-extrabold tracking-[-0.04em]">
                  {new Intl.NumberFormat(i18n.resolvedLanguage).format(1000)}
                </p>
                <p className="max-w-[16rem] text-base">{t('how.figure')}</p>
              </div>
            </Reveal>
          </div>
        </section>
      </div>
    </>
  )
}
