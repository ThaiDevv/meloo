import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import './home.css'

/** The five listening levels (BR-01). */
const LEVELS = [2, 5, 10, 20, 30] as const

/** The level at which the n-th letter shows up: spread over all five, the same on every render. */
function levelOf(index: number): number {
  return 1 + ((index * 7 + 2) % 5)
}

/** Text that is a ghost of itself until its level opens, then lights up. Decorative. */
function Lit({
  level,
  className,
  litClassName,
  children,
}: {
  level: number
  className?: string
  litClassName?: string
  children: ReactNode
}) {
  return (
    <span className={cn('relative inline-block', className)}>
      <span className="text-foreground/15">{children}</span>
      <span className={cn('mh-lit absolute inset-0', `mh-lit-${String(level)}`, litClassName)}>
        {children}
      </span>
    </span>
  )
}

/**
 * The page's one loud moment, between the hero and the modes: a title that is uncovered as the
 * listening level goes up. The question above it is real text; the lettering is decoration (the
 * ladder above already says it in words), so it is hidden from assistive technology.
 */
export function GuessBand() {
  const { t } = useTranslation('home')
  const letters = Array.from(t('guess.title').normalize('NFC'))
  return (
    <div className="bg-surface">
      <div className="mx-auto w-full max-w-6xl px-4 py-12 lg:py-16">
        <p className="text-lg">{t('guess.prompt')}</p>
        <div aria-hidden="true" className="mh-guess mt-6 select-none">
          <div className="flex flex-wrap font-display text-[clamp(2.5rem,10.5vw,9rem)] leading-none font-extrabold tracking-[-0.03em]">
            {letters.map((letter, index) =>
              letter === ' ' ? (
                <span key={index} className="inline-block w-[0.3em]" />
              ) : (
                <Lit
                  key={index}
                  level={levelOf(index)}
                  litClassName={levelOf(index) === 1 ? 'text-primary' : 'text-foreground'}
                >
                  {letter}
                </Lit>
              ),
            )}
          </div>
          <div className="num mt-8 flex justify-between text-sm font-semibold">
            {LEVELS.map((seconds, index) => (
              <Lit key={seconds} level={index + 1} litClassName="text-foreground">
                {t('guess.marker', { seconds })}
              </Lit>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
