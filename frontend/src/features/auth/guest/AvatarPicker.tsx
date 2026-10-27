import type { UseFormRegisterReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { AvatarBadge } from './AvatarBadge'
import { AVATARS } from './avatars'

/** Native radios: arrow keys and a visible focus ring come for free. */
export function AvatarPicker({ field }: { field: UseFormRegisterReturn }) {
  const { t } = useTranslation('auth')
  return (
    <fieldset>
      <legend className="mb-3 text-sm font-semibold">{t('guest.avatar.label')}</legend>
      <div className="grid grid-cols-4 gap-3 sm:grid-cols-8">
        {AVATARS.map(({ id }) => (
          <label key={id} className="relative cursor-pointer justify-self-start">
            <input type="radio" value={id} className="peer sr-only" {...field} />
            <AvatarBadge
              avatarId={id}
              className="transition-transform duration-150 peer-checked:ring-2 peer-checked:ring-primary peer-checked:ring-offset-2 peer-checked:ring-offset-surface peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-ring hover:scale-105 active:scale-95 motion-reduce:transition-none motion-reduce:hover:scale-100 motion-reduce:active:scale-100"
            />
            <span className="sr-only">{t(`guest.avatar.names.${id}`)}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
