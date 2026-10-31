import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { levelLabel } from '../lib/levels'
import type { GuessVerdict } from '../types'
import '../game-core.css'

interface GuessFeedbackProps {
  verdict: GuessVerdict | null | undefined
  /** Length of the level that is open now, named in the "not quite" message. */
  listenSeconds: number
}

/**
 * The server's verdict on the last guess. A polite live region, always in the page, so a screen
 * reader hears "Right artist!" without the player moving focus. BR-06: right artist, wrong song.
 */
export function GuessFeedback({ verdict, listenSeconds }: GuessFeedbackProps) {
  const { t } = useTranslation('game')
  const tone = !verdict
    ? null
    : verdict.correct
      ? 'correct'
      : verdict.rightArtist
        ? 'artist'
        : 'wrong'

  return (
    <div role="status" aria-live="polite" className="min-h-7">
      {verdict && tone ? (
        <p
          key={verdict.nonce}
          data-tone={tone}
          className={cn(
            'gc-verdict',
            tone === 'wrong' ? 'text-negative' : 'text-primary',
            tone === 'artist' ? 'text-xl font-bold' : 'text-base font-semibold',
          )}
        >
          {tone === 'correct' && t('feedback.correct')}
          {tone === 'artist' && (
            <>
              {t('feedback.rightArtist')}{' '}
              <span className="text-base font-medium text-muted-foreground">
                {t('feedback.rightArtistNote')}
              </span>
            </>
          )}
          {tone === 'wrong' && t('feedback.wrong', { label: levelLabel(listenSeconds) })}
        </p>
      ) : null}
    </div>
  )
}
