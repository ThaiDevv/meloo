import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { Input } from '@/shared/ui/Input'
import { useChatStore } from './store'

export interface RoomChatProps {
  roomId: string
}

export function RoomChat({ roomId }: RoomChatProps) {
  return <ChatFeed key={roomId} roomId={roomId} />
}

function ChatFeed({ roomId }: RoomChatProps) {
  const { t, i18n } = useTranslation('chat')
  const id = useId()
  const feed = useRef<HTMLDivElement>(null)
  const [draft, setDraft] = useState('')
  const [sendFailed, setSendFailed] = useState(false)
  const chat = useChatStore()
  const error = useRoomStore((state) => (state.roomId === roomId ? state.lastError : null))
  const dismissError = useRoomStore((state) => state.dismissError)
  useEffect(() => {
    useChatStore.getState().open(roomId)
  }, [roomId])
  const { link, send } = useRoomRealtime(roomId, {
    topics: ['chat'],
    onMessage: (message) => {
      if (message.type === 'CHAT_MESSAGE') useChatStore.getState().receive(roomId, message)
    },
  })
  const messages = chat.roomId === roomId ? chat.messages : []
  useEffect(() => {
    if (feed.current) feed.current.scrollTop = feed.current.scrollHeight
  }, [chat.messages, roomId])

  const warning =
    error &&
    (error.code === 'CHAT_ANSWER_LEAK' ||
      error.code === 'CHAT_PROHIBITED_WORD' ||
      error.code === 'RATE_LIMITED')
      ? t(`errors.${error.code}`)
      : null

  return (
    <section aria-labelledby={`${id}-title`} className="min-w-0 space-y-3">
      <h2 id={`${id}-title`} className="text-lg font-semibold">
        {t('title')}
      </h2>
      <div
        ref={feed}
        role="log"
        aria-label={t('messages')}
        tabIndex={0}
        className="max-h-72 space-y-4 overflow-y-auto rounded-2xl bg-surface p-4"
      >
        {messages.length === 0 ? <EmptyState title={t('empty')} /> : null}
        {messages.map((message) => (
          <article key={message.messageId} className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
              <span className="break-all font-semibold text-foreground">
                {message.senderNickname}
              </span>
              <time dateTime={message.sentAt} className="tabular-nums">
                {new Date(message.sentAt).toLocaleTimeString(i18n.resolvedLanguage, {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </time>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled
                title={t('reportUnavailable')}
                aria-label={t('reportMessage', { name: message.senderNickname })}
                aria-describedby={`${id}-report`}
                className="ml-auto"
              >
                {t('report')}
              </Button>
            </div>
            <p className="whitespace-pre-wrap break-all text-sm">{message.content}</p>
          </article>
        ))}
      </div>
      <p id={`${id}-report`} className="text-xs text-muted-foreground">
        {t('reportUnavailable')}
      </p>
      {link !== 'live' ? <p role="status">{t('connecting')}</p> : null}
      {warning ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-negative">
          <p className="grow">{warning}</p>
          <Button type="button" variant="ghost" size="sm" onClick={dismissError}>
            {t('dismiss')}
          </Button>
        </div>
      ) : null}
      {sendFailed ? <p role="alert">{t('sendFailed')}</p> : null}
      <form
        className="space-y-2"
        onSubmit={(event) => {
          event.preventDefault()
          const content = draft.trim()
          if (link !== 'live' || !content || content.length > 200) return
          dismissError()
          const sent = send('chat', { content })
          setSendFailed(!sent)
          if (sent) setDraft('')
        }}
      >
        <label htmlFor={`${id}-input`} className="text-sm font-medium">
          {t('input')}
        </label>
        <div className="flex gap-2">
          <Input
            id={`${id}-input`}
            value={draft}
            maxLength={200}
            autoComplete="off"
            aria-describedby={`${id}-limit`}
            onChange={(event) => {
              setDraft(event.target.value)
              setSendFailed(false)
            }}
          />
          <Button type="submit" disabled={link !== 'live' || !draft.trim() || draft.length > 200}>
            {t('send')}
          </Button>
        </div>
        <p id={`${id}-limit`} className="text-right text-xs text-muted-foreground tabular-nums">
          {t('limit', { count: draft.length })}
        </p>
      </form>
    </section>
  )
}
