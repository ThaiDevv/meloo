import { ArrowRight, Disc3, Trophy, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { SocialLoginButtons } from '@/features/auth/social/SocialLoginButtons'
import { Button } from '@/shared/ui/Button'
import './guest-invite.css'

/** Quick match is for signed-in accounts only (BR-13, BR-20). */
export function GuestInvite() {
  const { t } = useTranslation('room')

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-12 lg:py-16">
      <section
        aria-labelledby="quick-heading"
        data-testid="quick-guest-invite"
        className="quick-guest grid overflow-hidden rounded-2xl bg-surface md:grid-cols-2"
      >
        <div className="flex flex-col bg-raised p-6 sm:p-10 lg:p-12">
          <h1
            id="quick-heading"
            className="max-w-sm font-display text-4xl leading-[1.12] font-extrabold tracking-[-0.03em] text-balance lg:text-5xl"
          >
            {t('quick.title')}
          </h1>
          <p className="mt-4 max-w-sm leading-relaxed text-muted-foreground">{t('quick.intro')}</p>

          <div
            className="quick-guest-record hidden flex-1 items-center justify-center py-8 md:flex"
            aria-hidden="true"
          >
            <div className="quick-guest-disc">
              <Disc3 className="size-10 text-primary-foreground" strokeWidth={1.5} />
            </div>
          </div>

          <ul className="mt-6 flex flex-wrap gap-x-6 gap-y-3 text-sm font-medium md:mt-0">
            <li className="flex items-center gap-2">
              <Users aria-hidden="true" className="size-4 text-muted-foreground" />
              {t('quick.guest.multiplayer')}
            </li>
            <li className="flex items-center gap-2">
              <Trophy aria-hidden="true" className="size-4 text-primary" />
              {t('quick.guest.ranked')}
            </li>
          </ul>
        </div>

        <div className="flex flex-col justify-center p-6 sm:p-10 lg:p-12">
          <h2 className="font-display text-2xl leading-tight font-extrabold tracking-[-0.02em] text-balance lg:text-3xl">
            {t('quick.guest.title')}
          </h2>
          <p className="mt-4 leading-relaxed text-muted-foreground">
            {t('quick.guest.description')}
          </p>

          <div className="quick-guest-auth mt-8">
            <SocialLoginButtons returnTo="/quick-match" />
          </div>
          <p className="mt-4 text-center text-sm leading-relaxed text-muted-foreground">
            {t('quick.guest.returnHint')}
          </p>

          <div className="mt-8 border-t border-border pt-6">
            <p className="text-sm text-muted-foreground">{t('quick.guest.explore')}</p>
            <Button
              asChild
              variant="ghost"
              className="mt-2 h-auto min-h-11 w-full justify-between gap-3 px-0 text-sm whitespace-normal hover:bg-transparent hover:text-primary"
            >
              <Link to="/rooms">
                {t('quick.guest.browse')}
                <ArrowRight aria-hidden="true" className="size-5 shrink-0" />
              </Link>
            </Button>
          </div>
        </div>
      </section>
    </div>
  )
}
