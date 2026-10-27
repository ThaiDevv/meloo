import { cn } from '@/shared/lib/utils'

/** Text logo: the two o's are the records, in the accent. Decorative, so the link around it carries the name. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('font-display text-xl font-extrabold tracking-tight', className)}>
      Mel<span className="text-primary">oo</span>
    </span>
  )
}
