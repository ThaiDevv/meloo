import { Search } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/Button'
import { SEARCH_DEBOUNCE_MS } from '../lib/search'
import { splitMatch } from '../lib/text'
import type { TrackSuggestion } from '../types'

interface GuessBoxProps {
  /** Suggestions for the last query handed to `onSearch`, supplied by the host (catalog-wide, BR-05). */
  suggestions: readonly TrackSuggestion[]
  /** Must be true from the moment `onSearch` is called until its suggestions arrive, so old ones are never offered. */
  loading?: boolean
  /** A guess request is in flight. */
  disabled?: boolean
  /** Called 200 ms after the player stops typing, with the trimmed text. An empty string means "clear". */
  onSearch: (query: string) => void
  onGuess: (trackId: string) => void
}

/**
 * A combobox: type, move through suggestions with the arrow keys, Enter to pick, Enter again (or the
 * button) to guess. A wrong pick costs a level, so picking and guessing are two deliberate steps.
 */
export function GuessBox({
  suggestions,
  loading = false,
  disabled = false,
  onSearch,
  onGuess,
}: GuessBoxProps) {
  const { t } = useTranslation('game')
  const inputId = useId()
  const listId = useId()
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<TrackSuggestion | null>(null)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const timer = useRef<number | undefined>(undefined)
  const onSearchRef = useRef(onSearch)

  useEffect(() => {
    onSearchRef.current = onSearch
  })
  useEffect(
    () => () => {
      window.clearTimeout(timer.current)
    },
    [],
  )

  const showList = open && !selected && text.trim() !== ''
  // Suggestions answer the query the host was last asked. While the player has typed on, or a
  // search is still running, they are for another text: showing them would let a wrong song be picked.
  const settled = query === text.trim() && !loading
  const listed = showList && settled ? suggestions : []
  const optionId = (index: number) => `${listId}-${String(index)}`

  function handleChange(value: string) {
    setText(value)
    setSelected(null)
    setActive(-1)
    setOpen(true)
    window.clearTimeout(timer.current)
    const trimmed = value.trim()
    timer.current = window.setTimeout(() => {
      setQuery(trimmed)
      onSearchRef.current(trimmed)
    }, SEARCH_DEBOUNCE_MS)
  }

  function pick(track: TrackSuggestion) {
    window.clearTimeout(timer.current)
    setSelected(track)
    setText(`${track.title} – ${track.artistName}`)
    setOpen(false)
    setActive(-1)
  }

  function submit() {
    if (!selected || disabled) return
    window.clearTimeout(timer.current)
    onGuess(selected.id)
    setSelected(null)
    setText('')
    setQuery('')
    onSearchRef.current('')
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (listed.length === 0) return
      event.preventDefault()
      setOpen(true)
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((current) =>
        current < 0
          ? step === 1
            ? 0
            : listed.length - 1
          : (current + step + listed.length) % listed.length,
      )
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const highlighted = listed[active]
      if (highlighted) pick(highlighted)
      else submit()
    } else if (event.key === 'Escape') {
      setOpen(false)
      setActive(-1)
    }
  }

  const status = showList
    ? !settled
      ? t('guess.searching')
      : listed.length === 0
        ? t('guess.empty')
        : t('guess.results', { count: listed.length })
    : selected
      ? t('guess.selected', { title: selected.title, artist: selected.artistName })
      : ''

  return (
    <div className="relative">
      <label htmlFor={inputId} className="mb-2 block text-sm font-semibold">
        {t('guess.label')}
      </label>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-faint"
            strokeWidth={2}
          />
          <input
            id={inputId}
            type="text"
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && active >= 0 ? optionId(active) : undefined}
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
            disabled={disabled}
            value={text}
            placeholder={t('guess.placeholder')}
            onChange={(event) => {
              handleChange(event.target.value)
            }}
            onFocus={() => {
              if (!selected) setOpen(true)
            }}
            onBlur={() => {
              setOpen(false)
            }}
            onKeyDown={handleKeyDown}
            className="h-12 w-full rounded-xl bg-raised pr-3.5 pl-10 text-base text-foreground transition-colors placeholder:text-faint focus-visible:outline-2 disabled:opacity-50 motion-reduce:transition-none"
          />
        </div>
        <Button onClick={submit} disabled={!selected || disabled} className="h-12 px-6">
          {t('guess.submit')}
        </Button>
      </div>

      <p role="status" className="sr-only">
        {status}
      </p>

      {/* The suggestion list floats over the page, the one place a shadow belongs. taste-skill-ignore */}
      <ul
        id={listId}
        role="listbox"
        aria-label={t('guess.label')}
        hidden={!showList}
        className="absolute inset-x-0 z-10 mt-2 max-h-72 overflow-y-auto rounded-xl bg-raised p-1.5 shadow-lg shadow-black/40"
      >
        {showList && !settled ? (
          <li role="presentation" className="px-3 py-3 text-sm text-muted-foreground">
            {t('guess.searching')}
          </li>
        ) : null}
        {showList && settled && listed.length === 0 ? (
          <li role="presentation" className="px-3 py-3 text-sm text-muted-foreground">
            {t('guess.empty')}
          </li>
        ) : null}
        {listed.map((track, index) => (
          <li
            key={track.id}
            id={optionId(index)}
            role="option"
            aria-selected={index === active}
            // Picked on pointerup, which comes before the blur and the emulated mouse events of a tap.
            // Not on pointerdown: a finger that scrolls the list ends in pointercancel, not pointerup.
            // mousedown only keeps focus in the input (so the list stays open) on desktop.
            onPointerUp={() => {
              pick(track)
            }}
            onMouseDown={(event) => {
              event.preventDefault()
            }}
            onMouseEnter={() => {
              setActive(index)
            }}
            className={cn(
              'flex cursor-pointer flex-col rounded-lg px-3 py-2.5',
              index === active && 'bg-foreground/10',
            )}
          >
            <span className="text-sm font-medium text-foreground">
              <Highlight text={track.title} query={query} />
            </span>
            <span className="text-xs text-muted-foreground">
              <Highlight text={track.artistName} query={query} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Highlight({ text, query }: { text: string; query: string }) {
  return (
    <>
      {splitMatch(text, query).map((part, index) =>
        part.match ? (
          <mark key={index} className="bg-transparent font-bold text-primary">
            {part.text}
          </mark>
        ) : (
          <span key={index}>{part.text}</span>
        ),
      )}
    </>
  )
}
