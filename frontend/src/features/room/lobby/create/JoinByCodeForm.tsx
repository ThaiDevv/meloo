import { useId, useState, type SubmitEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { readRoomCode } from '../model/roomCode'

/** Join with a code or a pasted invitation link; the room page does the actual joining. */
export function JoinByCodeForm() {
  const { t } = useTranslation('room')
  const navigate = useNavigate()
  const id = useId()
  const [value, setValue] = useState('')
  const [invalid, setInvalid] = useState(false)

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    const code = readRoomCode(value)
    if (!code) {
      setInvalid(true)
      return
    }
    void navigate(`/r/${code}`)
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4 rounded-2xl bg-surface p-6">
      <label htmlFor={id} className="block font-display text-2xl font-extrabold tracking-[-0.02em]">
        {t('join.label')}
      </label>
      <div className="flex gap-2">
        <Input
          id={id}
          value={value}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          placeholder={t('join.placeholder')}
          aria-invalid={invalid}
          aria-describedby={invalid ? `${id}-error` : undefined}
          className="h-14 text-lg font-semibold tracking-widest uppercase placeholder:text-base placeholder:font-normal placeholder:tracking-normal placeholder:normal-case"
          onChange={(event) => {
            setValue(event.target.value)
            setInvalid(false)
          }}
        />
        <Button type="submit" variant="outline" className="h-14 px-6 text-base">
          {t('join.submit')}
        </Button>
      </div>
      {invalid ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-negative">
          {t('join.invalid')}
        </p>
      ) : null}
    </form>
  )
}
