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
    <section aria-labelledby="room-code-heading" className="space-y-4">
      <h2 id="room-code-heading" className="text-base font-semibold opacity-80">
        {t('code.heading')}
      </h2>
      <p
        role="img"
        aria-label={code.split('').join(' ')}
        className="num font-display text-[clamp(2.75rem,14vw,4.75rem)] leading-none font-extrabold tracking-[0.12em] select-all"
      >
        <span>{code.slice(0, 3)}</span>
        <span className="ml-[0.3em]">{code.slice(3)}</span>
      </p>
      <div className="flex flex-wrap gap-2">
        <Button
          className="bg-primary-foreground text-primary"
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
          className="border-primary-foreground/50 text-primary-foreground hover:bg-primary-foreground/10"
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
