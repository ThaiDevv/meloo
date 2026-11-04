import { Check, Copy, Link2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { toast } from '@/shared/ui/toast-store'
import { inviteLink } from '../model/roomCode'

type Copied = 'code' | 'link' | null

/** Copies with the Clipboard API; false when the browser refuses (insecure page, no permission). */
async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

/**
 * The code is what people read out loud or type from a message, so it is the biggest thing on the
 * lobby: display face, wide tracking, split in two groups of three. Selecting it copies the plain code.
 */
export function RoomCode({ code }: { code: string }) {
  const { t } = useTranslation('room')
  const [copied, setCopied] = useState<Copied>(null)

  const copy = async (what: Exclude<Copied, null>) => {
    const ok = await copyText(what === 'code' ? code : inviteLink(code))
    if (!ok) {
      toast({
        title: t('code.copyFailed'),
        description: t('code.copyFailedHint'),
        tone: 'negative',
      })
      return
    }
    setCopied(what)
    toast({ title: t(what === 'code' ? 'code.copiedCode' : 'code.copiedLink'), tone: 'positive' })
    setTimeout(() => {
      setCopied((current) => (current === what ? null : current))
    }, 2000)
  }

  return (
    <section aria-labelledby="room-code-heading" className="space-y-3">
      <h2 id="room-code-heading" className="text-sm font-medium text-muted-foreground">
        {t('code.heading')}
      </h2>
      <p
        role="img"
        aria-label={code.split('').join(' ')}
        className="num font-display text-[clamp(2.5rem,12vw,3.75rem)] leading-none font-extrabold tracking-[0.14em] text-foreground select-all"
      >
        <span>{code.slice(0, 3)}</span>
        <span className="ml-[0.35em]">{code.slice(3)}</span>
      </p>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            void copy('code')
          }}
        >
          {copied === 'code' ? (
            <Check aria-hidden="true" className="size-4" />
          ) : (
            <Copy aria-hidden="true" className="size-4" />
          )}
          {t('code.copyCode')}
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            void copy('link')
          }}
        >
          {copied === 'link' ? (
            <Check aria-hidden="true" className="size-4" />
          ) : (
            <Link2 aria-hidden="true" className="size-4" />
          )}
          {t('code.copyLink')}
        </Button>
      </div>
    </section>
  )
}
