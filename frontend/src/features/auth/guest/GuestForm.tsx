import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { CreateGuestError, useCreateGuest } from './api/useCreateGuest'
import { AvatarPicker } from './AvatarPicker'
import { AVATARS } from './avatars'
import { guestFormSchema, NICKNAME_MAX, NICKNAME_MIN, type GuestFormValues } from './nickname'

const FAILURE_KEY = {
  rejected: 'guest.error.rejected',
  'rate-limited': 'guest.error.rateLimited',
  unknown: 'guest.error.unknown',
} as const

export function GuestForm() {
  const { t } = useTranslation('auth')
  const createGuest = useCreateGuest()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<GuestFormValues>({
    resolver: zodResolver(guestFormSchema),
    defaultValues: { nickname: '', avatarId: AVATARS[0]?.id },
  })

  const onSubmit = handleSubmit((values) => {
    createGuest.mutate(values, {
      onError: (error) => {
        const reason = error instanceof CreateGuestError ? error.reason : 'unknown'
        // A 400 is about the nickname (length or banned word): show it on the field.
        setError(reason === 'rejected' ? 'nickname' : 'root', {
          type: 'server',
          message: FAILURE_KEY[reason],
        })
      },
    })
  })

  const nicknameError = errors.nickname?.message
  const rootError = errors.root?.message

  return (
    <form
      noValidate
      onSubmit={(event) => {
        void onSubmit(event)
      }}
      className="space-y-8 rounded-2xl bg-surface p-6 sm:p-8"
    >
      <div className="space-y-2">
        <label htmlFor="guest-nickname" className="block text-sm font-semibold">
          {t('guest.nickname.label')}
        </label>
        <Input
          id="guest-nickname"
          autoComplete="nickname"
          autoFocus
          maxLength={NICKNAME_MAX * 2}
          aria-invalid={nicknameError ? true : undefined}
          aria-describedby={
            nicknameError ? 'guest-nickname-hint guest-nickname-error' : 'guest-nickname-hint'
          }
          {...register('nickname')}
        />
        <p id="guest-nickname-hint" className="text-sm text-muted-foreground">
          {t('guest.nickname.hint', { min: NICKNAME_MIN, max: NICKNAME_MAX })}
        </p>
        {nicknameError ? (
          <p id="guest-nickname-error" role="alert" className="text-sm font-medium text-negative">
            {t(nicknameError)}
          </p>
        ) : null}
      </div>

      <AvatarPicker field={register('avatarId')} />

      <div className="space-y-3">
        {rootError ? (
          <p role="alert" className="text-sm font-medium text-negative">
            {t(rootError)}
          </p>
        ) : null}
        <Button type="submit" disabled={createGuest.isPending} className="w-full sm:w-auto">
          {createGuest.isPending ? t('guest.submitting') : t('guest.submit')}
        </Button>
      </div>
    </form>
  )
}
