import { Check, Play } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Equalizer } from '@/shared/music-picker'
import './home.css'

const STEPS = ['listen', 'more', 'guess', 'score'] as const
type Step = (typeof STEPS)[number]
const LEVELS = [2, 5, 10, 20, 30] as const
const LONGEST = 30

// The stairs go one column further right at every step on a desktop; on a phone the indent is small.
const PLACEMENT = ['', 'lg:ml-6', 'lg:ml-12', 'lg:ml-[4.5rem]'] as const

const ENTER =
  'animate-in fade-in slide-in-from-bottom-2 duration-300 fill-mode-both motion-reduce:animate-none'

/**
 * A made-up round, only to show how one goes. The story is the same in every language: the first
 * 2 seconds do not give it away, "hear more" opens 5 seconds, the player types the title without
 * accents, picks it from the suggestions, and it is right at the 5 second level (BR-02: 800 base).
 */
function RoundMock({ step }: { step: Step }) {
  const { t } = useTranslation('home')
  const levelIndex = step === 'listen' ? 0 : 1
  const solved = step === 'score'
  const typing = step === 'guess' || solved
  return (
    <div
      aria-hidden="true"
      className="mx-auto w-full max-w-md rounded-3xl bg-surface p-5 select-none sm:p-6"
    >
      <div className="flex items-baseline justify-between text-sm text-muted-foreground">
        <span>{t('demo.round')}</span>
        <span className="num">
          {t('demo.score')}{' '}
          <span className="font-semibold text-foreground">{solved ? '2.000' : '1.200'}</span>
        </span>
      </div>

      <div className="mt-5 flex gap-[3px]">
        {LEVELS.map((seconds, index) => {
          const share = ((seconds - (LEVELS[index - 1] ?? 0)) / LONGEST) * 100
          return (
            <div
              key={seconds}
              style={{ flexBasis: `${String(share)}%` }}
              className="min-w-0 flex-none"
            >
              <span
                className={cn(
                  'num mb-1.5 block text-center text-sm font-semibold transition-colors duration-300',
                  index === levelIndex ? 'text-foreground' : 'text-muted-foreground',
                )}
              >
                {seconds}s
              </span>
              <span
                className={cn(
                  'block h-6 transition-colors duration-300',
                  index === 0 && 'rounded-l-full',
                  index === LEVELS.length - 1 && 'rounded-r-full',
                  index === levelIndex
                    ? 'bg-primary'
                    : index < levelIndex
                      ? 'bg-foreground/40'
                      : 'bg-foreground/[0.08]',
                )}
              />
            </div>
          )
        })}
      </div>

      <div className="mt-5 flex items-center gap-4">
        <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
          {solved ? (
            <Check className="size-6" strokeWidth={2.5} />
          ) : (
            <Play className="size-6 fill-current" />
          )}
        </span>
        <div className="min-w-0 flex-1">
          <Equalizer paused={solved} className="text-3xl text-primary" />
          <p className="num mt-1 text-sm text-muted-foreground">
            0:{String(LEVELS[levelIndex]).padStart(2, '0')} / 0:30
          </p>
        </div>
        <span
          key={step}
          className={cn(
            'rounded-full px-4 py-2 text-sm font-semibold',
            step === 'more' ? 'mv-pop-once bg-primary text-primary-foreground' : 'bg-foreground/10',
            typing && 'invisible',
          )}
        >
          {t('demo.more')}
        </span>
      </div>

      <div
        className={cn(
          'mt-5 flex h-11 items-center rounded-xl bg-background px-4 text-sm',
          solved && 'text-primary',
        )}
      >
        {typing ? (
          <span key={step} className={cn(ENTER, 'font-semibold')}>
            {solved ? t('demo.title') : t('demo.typed')}
          </span>
        ) : (
          <span className="text-muted-foreground">{t('demo.placeholder')}</span>
        )}
      </div>

      <div className="mt-2 min-h-24">
        {step === 'guess' ? (
          <div key={step} className={ENTER}>
            <div className="rounded-xl bg-foreground/10 px-4 py-2.5 text-sm font-semibold">
              {t('demo.title')}
            </div>
            <div className="px-4 py-2.5 text-sm text-muted-foreground">{t('demo.other')}</div>
          </div>
        ) : null}
        {solved ? (
          <div
            key={step}
            className={cn(
              ENTER,
              'flex items-center justify-between rounded-xl bg-primary px-4 py-3 text-primary-foreground',
            )}
          >
            <span className="font-semibold">{t('demo.right')}</span>
            <span className="num font-display text-2xl font-extrabold">+800</span>
          </div>
        ) : null}
      </div>
    </div>
  )
}

/**
 * "A round, step by step": the four steps on one side, and on the other a small made-up round that
 * changes with the step. The steps advance on their own (a bar fills under the active one) until
 * the player hovers or focuses the demo, or presses a step. The mock is decoration: every thing it
 * shows is also said in the step texts, so it is hidden from assistive technology.
 */
export function RoundDemo() {
  const { t } = useTranslation('home')
  const [active, setActive] = useState(0)
  const step = STEPS[active] ?? 'listen'

  return (
    <div className="mh-demo mt-10 grid gap-10 lg:grid-cols-12 lg:items-center">
      <ol className="grid gap-3 lg:col-span-6">
        {STEPS.map((name, index) => {
          const current = index === active
          return (
            <li key={name} className={PLACEMENT[index]}>
              <button
                type="button"
                aria-current={current ? 'step' : undefined}
                onClick={() => {
                  setActive(index)
                }}
                className={cn(
                  'w-full rounded-2xl p-5 text-left transition-colors duration-200 motion-reduce:transition-none',
                  current ? 'bg-surface' : 'hover:bg-surface/60',
                )}
              >
                <span
                  className={cn(
                    'block font-display text-[clamp(1.5rem,3vw,2.25rem)] leading-tight font-extrabold tracking-[-0.025em] transition-colors duration-200 motion-reduce:transition-none',
                    current
                      ? name === 'score'
                        ? 'text-primary'
                        : 'text-foreground'
                      : 'text-foreground/45',
                  )}
                >
                  {t(`how.steps.${name}.title`)}
                </span>
                <span
                  className={cn(
                    'mt-2 block max-w-sm text-base',
                    current ? 'text-foreground' : 'text-muted-foreground',
                  )}
                >
                  {t(`how.steps.${name}.body`)}
                </span>
                <span className="mt-4 block h-1 overflow-hidden rounded-full bg-foreground/10">
                  {current ? (
                    <span
                      key={active}
                      onAnimationEnd={() => {
                        setActive((value) => (value + 1) % STEPS.length)
                      }}
                      className="mh-step-bar block h-full rounded-full bg-primary"
                    />
                  ) : null}
                </span>
              </button>
            </li>
          )
        })}
      </ol>
      <div className="lg:col-span-5 lg:col-start-8">
        <RoundMock step={step} />
      </div>
    </div>
  )
}
