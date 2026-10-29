import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/Button'
import { Reveal } from './Reveal'

/**
 * The scenes the catalog is built around (the music source was measured for V-pop, K-pop and US-UK,
 * SPK-01) and the colour field of each. The first one is the widest.
 */
const SCENES = [
  {
    id: 'vpop',
    span: 'lg:col-span-6',
    size: 'text-[clamp(2.75rem,7vw,5.5rem)]',
    tone: 'bg-primary text-primary-foreground',
  },
  {
    id: 'kpop',
    span: 'lg:col-span-3',
    size: 'text-[clamp(2.75rem,4.6vw,4.25rem)]',
    tone: 'bg-foreground text-background',
  },
  {
    id: 'usuk',
    span: 'lg:col-span-3',
    size: 'text-[clamp(2.75rem,4.6vw,4.25rem)]',
    tone: 'bg-raised text-foreground',
  },
] as const
/** The ways to pick tracks, as the music picker offers them (FR-05). */
const FACETS = ['genre', 'country', 'decade', 'artist', 'playlist'] as const

/** "Music for every taste": the catalog's range, in three big colour fields and the ways to pick. */
export function MusicMix() {
  const { t } = useTranslation('home')
  return (
    <section aria-labelledby="mix-title" className="pb-20 lg:pb-32">
      <Reveal>
        <h2
          id="mix-title"
          className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl"
        >
          {t('mix.heading')}
        </h2>
        <p className="mt-3 max-w-xl text-lg">{t('mix.body')}</p>
      </Reveal>
      <div className="mt-8 grid gap-3 lg:grid-cols-12">
        {SCENES.map((scene, index) => (
          <Reveal key={scene.id} delay={index * 80} className={scene.span}>
            <div
              className={cn(
                'flex min-h-48 flex-col justify-between gap-10 rounded-2xl p-6 transition-transform duration-200 hover:-translate-y-1 motion-reduce:transition-none motion-reduce:hover:translate-y-0 lg:h-full lg:min-h-64 lg:p-8',
                scene.tone,
              )}
            >
              <p
                className={cn(
                  'font-display leading-none font-extrabold tracking-[-0.04em] whitespace-nowrap',
                  scene.size,
                )}
              >
                {t(`mix.scenes.${scene.id}.name`)}
              </p>
              <p className="text-base">{t(`mix.scenes.${scene.id}.note`)}</p>
            </div>
          </Reveal>
        ))}
      </div>
      <Reveal delay={240} className="mt-6 flex flex-wrap items-center gap-2">
        <p className="mr-2 text-base">{t('mix.pick')}</p>
        {FACETS.map((facet) => (
          <span key={facet} className="rounded-full bg-raised px-4 py-2 text-sm font-medium">
            {t(`mix.facets.${facet}`)}
          </span>
        ))}
        <Button asChild variant="outline" className="ml-auto">
          <Link to="/solo">
            {t('mix.cta')}
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </Button>
      </Reveal>
    </section>
  )
}
