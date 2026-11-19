import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Field } from '../../shell/fields'
import { ConfirmChange, QueryState } from '../../users/components/AdminFeedback'
import { useModerationChange, useWords, type Word } from '../api/moderationApi'

export function BannedWords() {
  const { t } = useTranslation('admin')
  const query = useWords()
  const mutation = useModerationChange()
  const [editor, setEditor] = useState<{ word?: Word; deleting?: boolean } | null>(null)
  const [text, setText] = useState('')
  const [saved, setSaved] = useState(false)
  const open = (word?: Word, deleting = false) => {
    mutation.reset()
    setSaved(false)
    setText(word?.word ?? '')
    setEditor({ word, deleting })
  }
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-prose text-sm text-muted-foreground">{t('words.hint')}</p>
        <Button
          onClick={() => {
            open()
          }}
        >
          {t('words.add')}
        </Button>
      </div>
      {saved && (
        <p role="status" className="text-sm text-positive">
          {t('manage.saved')}
        </p>
      )}
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={query.data?.items.length === 0}
        retry={() => void query.refetch()}
      >
        <ul className="divide-y divide-border rounded-2xl border border-border">
          {query.data?.items.map((word) => (
            <li key={word.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
              <span className="min-w-0 flex-1 text-sm break-words">{word.word}</span>
              <div className="flex gap-1">
                <Button
                  variant="ghost"
                  aria-label={t('words.editNamed', { word: word.word })}
                  onClick={() => {
                    open(word)
                  }}
                >
                  {t('words.edit')}
                </Button>
                <Button
                  variant="destructive"
                  aria-label={t('words.deleteNamed', { word: word.word })}
                  onClick={() => {
                    open(word, true)
                  }}
                >
                  {t('words.delete')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </QueryState>
      {editor && (
        <ConfirmChange
          title={t(editor.deleting ? 'words.delete' : editor.word ? 'words.edit' : 'words.add')}
          description={
            editor.deleting
              ? t('words.deleteConfirm', { word: editor.word?.word })
              : t('words.hint')
          }
          pending={mutation.isPending}
          error={mutation.error}
          disabled={!editor.deleting && !text.trim()}
          onClose={() => {
            setEditor(null)
          }}
          onConfirm={() => {
            const change =
              editor.deleting && editor.word
                ? { type: 'deleteWord' as const, id: editor.word.id }
                : { type: 'word' as const, id: editor.word?.id, word: text.trim() }
            mutation.mutate(change, {
              onSuccess: () => {
                setEditor(null)
                setSaved(true)
              },
            })
          }}
        >
          {!editor.deleting && (
            <Field label={t('words.word')} htmlFor="banned-word">
              <Input
                id="banned-word"
                value={text}
                required
                maxLength={80}
                onChange={(e) => {
                  setText(e.target.value)
                }}
              />
            </Field>
          )}
        </ConfirmChange>
      )}
    </div>
  )
}
