import type { components } from '@/shared/api/schema.gen'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/Avatar'
import { Badge } from '@/shared/ui/Badge'

export interface RowData {
  userId: string
  rank: number
  nickname: string
  avatarUrl?: string | null
  avatarId?: components['schemas']['PresetAvatarId'] | null
  /** The main number of the board, already formatted. */
  value: string
  /** One short line under the name. */
  detail: string
}

interface LeaderboardRowProps {
  row: RowData
  isMe: boolean
  as?: 'li' | 'div'
}

export function LeaderboardRow({ row, isMe, as: Tag = 'li' }: LeaderboardRowProps) {
  const { t } = useTranslation('leaderboard')
  return (
    <Tag
      aria-current={isMe ? 'true' : undefined}
      data-testid={isMe ? 'leaderboard-row-me' : 'leaderboard-row'}
      className={cn(
        'flex items-center gap-3 rounded-xl px-3 py-2.5',
        isMe ? 'bg-primary/10 ring-1 ring-primary/40' : 'odd:bg-raised/40',
      )}
    >
      <span className="w-10 shrink-0 text-right text-sm font-semibold tabular-nums">
        <span className="sr-only">{t('row.rank')} </span>
        {row.rank}
      </span>
      <Avatar
        name={row.nickname}
        src={row.avatarUrl ?? undefined}
        avatarId={row.avatarId}
        size="sm"
      />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className="truncate text-sm font-medium">{row.nickname}</span>
          {isMe ? <Badge tone="positive">{t('row.you')}</Badge> : null}
        </span>
        <span className="block truncate text-xs text-muted-foreground">{row.detail}</span>
      </span>
      <span className="shrink-0 text-sm font-semibold tabular-nums">{row.value}</span>
    </Tag>
  )
}
