import { VinylDisc } from '@/shared/music-picker'

/**
 * The name at the top of the page, set huge. The two o's are two records, each turning at its own
 * pace, with the accent on their labels as in the logo. The header already names the site for
 * assistive technology, so this is decoration and is hidden from it.
 */
export function Wordmark() {
  return (
    <p
      aria-hidden="true"
      className="motion-rise flex items-center font-display text-[clamp(4.5rem,18vw,13rem)] leading-[0.8] font-extrabold tracking-[-0.06em] text-foreground select-none"
    >
      <span>Mel</span>
      <VinylDisc
        accent
        arm={false}
        className="mx-[0.03em] w-[0.6em] translate-y-[0.05em] text-foreground"
      />
      <VinylDisc accent arm={false} className="w-[0.6em] translate-y-[0.05em] text-foreground" />
    </p>
  )
}
