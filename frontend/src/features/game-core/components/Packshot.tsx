import { useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { CoverArt } from './CoverArt'
import '../game-core.css'

interface PackshotProps {
  /** `coverUrl` of the answer; missing, empty or broken shows the stand-in sleeve and a lime label. */
  coverUrl?: string
  title: string
  artistName: string
  correct: boolean
  className?: string
}

/**
 * The answer as a record in its sleeve: the cover in front, the record sliding out from behind it
 * with the cover as its label. The mystery disc of the round, now with a name.
 */
export function Packshot({ coverUrl, title, artistName, correct, className }: PackshotProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const usable = coverUrl && coverUrl !== failedUrl ? coverUrl : undefined

  return (
    <div
      data-outcome={correct ? 'correct' : 'wrong'}
      className={cn('gc-cover relative aspect-[100/66] w-full', className)}
    >
      <div aria-hidden="true" className="absolute inset-y-[4%] left-[35.6%] aspect-square">
        <div className="gc-record relative size-full">
          <svg viewBox="0 0 200 200" focusable="false" className="size-full">
            <circle cx="100" cy="100" r="99" className="fill-surface" />
            <circle
              cx="100"
              cy="100"
              r="98"
              fill="none"
              stroke="var(--foreground)"
              strokeOpacity={0.14}
              strokeWidth={2}
            />
            {Array.from({ length: 16 }, (_, i) => 40 + i * 3.6).map((radius) => (
              <circle
                key={radius}
                cx="100"
                cy="100"
                r={radius}
                fill="none"
                stroke="var(--foreground)"
                strokeOpacity={0.07}
              />
            ))}
            <circle cx="100" cy="100" r="34" className="fill-primary" />
          </svg>
          {usable ? (
            <img
              src={usable}
              alt=""
              referrerPolicy="no-referrer"
              className="absolute top-[33%] left-[33%] size-[34%] rounded-full object-cover"
            />
          ) : null}
          <span className="absolute top-1/2 left-1/2 size-[3%] -translate-1/2 rounded-full bg-background" />
        </div>
      </div>

      <div className="@container relative h-full w-[66%]">
        <CoverArt
          url={usable}
          title={title}
          fallback={
            // No artwork: a sleeve set in type, like a plain-cover pressing.
            <span className="flex size-full flex-col justify-between p-[9%] text-left">
              <span className="line-clamp-4 pb-1 font-display text-[clamp(1.5rem,11cqw,2.75rem)] leading-[1.12] font-extrabold tracking-tight break-words text-foreground">
                {title}
              </span>
              <span className="text-sm font-semibold text-primary">{artistName}</span>
            </span>
          }
          onError={() => {
            setFailedUrl(coverUrl ?? null)
          }}
          className="size-full rounded-xl"
        />
        {correct ? (
          <span
            aria-hidden="true"
            className="gc-burst pointer-events-none absolute inset-0 rounded-xl border-2 border-primary"
          />
        ) : null}
      </div>
    </div>
  )
}
