import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { VinylDisc } from '@/shared/music-picker'
import { cn } from '@/shared/lib/utils'

interface CoverArtProps {
  /** `coverUrl` of the answer. Missing, empty (the contract allows "") or broken all show the same stand-in. */
  url?: string
  title: string
  /** Drawn in place of a missing cover; a record by default. */
  fallback?: ReactNode
  /** The image failed to load (the stand-in is already shown). */
  onError?: () => void
  className?: string
}

/** The cover on the answer screen. It never fails the screen: no image just means a record in its place. */
export function CoverArt({ url, title, fallback, onError, className }: CoverArtProps) {
  const { t } = useTranslation('game')
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const usable = url !== undefined && url !== '' && url !== failedUrl

  return (
    <div className={cn('relative aspect-square overflow-hidden rounded-2xl bg-raised', className)}>
      {usable ? (
        <img
          src={url}
          alt={t('reveal.coverAlt', { title })}
          referrerPolicy="no-referrer"
          onError={() => {
            setFailedUrl(url)
            onError?.()
          }}
          className="size-full object-cover"
        />
      ) : (
        <div
          role="img"
          aria-label={t('reveal.coverMissing')}
          className="flex size-full items-center justify-center text-faint"
        >
          {fallback ?? <VinylDisc spinning={false} className="w-3/5" />}
        </div>
      )}
    </div>
  )
}
