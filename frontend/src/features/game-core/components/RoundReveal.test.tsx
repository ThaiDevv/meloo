import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import {
  mockResultEmptyCover,
  mockResultNoCover,
  mockResultWithCover,
  mockResultWrongOut,
} from '../mocks/fixtures'
import { RoundReveal } from './RoundReveal'

describe('RoundReveal', () => {
  it('shows the answer, the cover and the source link when the cover exists', () => {
    render(<RoundReveal result={mockResultWithCover} />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Chúng Ta Của Hiện Tại')
    expect(screen.getByText('Sơn Tùng M-TP')).toBeInTheDocument()
    expect(
      screen.getByRole('img', { name: 'Ảnh bìa bài Chúng Ta Của Hiện Tại' }),
    ).toBeInTheDocument()
    const link = screen.getByRole('link', { name: /Nghe đầy đủ trên Deezer/ })
    expect(link).toHaveAttribute('href', 'https://www.deezer.com/track/1234567890')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  it.each([
    ['no coverUrl', mockResultNoCover],
    ['an empty coverUrl', mockResultEmptyCover],
  ])('shows a stand-in and a complete answer with %s', (_name, result) => {
    render(<RoundReveal result={result} />)
    expect(screen.queryByRole('img', { name: /Ảnh bìa bài/ })).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Chưa có ảnh bìa' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Chúng Ta Của Hiện Tại')
    expect(screen.getByRole('link', { name: /Deezer/ })).toBeInTheDocument()
  })

  it('falls back to the stand-in when the image fails to load', async () => {
    render(<RoundReveal result={mockResultWithCover} />)
    const image = screen.getByRole('img', { name: /Ảnh bìa bài/ })
    image.dispatchEvent(new Event('error'))
    expect(await screen.findByRole('img', { name: 'Chưa có ảnh bìa' })).toBeInTheDocument()
  })

  it('breaks the points into base, combo and speed (AC 3)', () => {
    render(<RoundReveal result={mockResultWithCover} />)
    const rows = new Map(
      screen
        .getAllByRole('term')
        .map((term) => [term.textContent, term.nextElementSibling?.textContent]),
    )
    expect(rows.get('Điểm cơ bản')).toBe('800')
    expect(rows.get('Combo')).toBe('+10% (+80)')
    expect(rows.get('Thưởng tốc độ')).toBe('—không có')
    expect(rows.get('Điểm vòng')).toBe('880')
    expect(screen.getByText('Đúng ở mức 5s')).toBeInTheDocument()
  })

  it('uses singular forms in English', async () => {
    await i18n.changeLanguage('en')
    render(<RoundReveal result={{ ...mockResultWithCover, wrongCount: 1, comboStreak: 1 }} />)
    expect(screen.getByText('1 wrong guess')).toBeInTheDocument()
    expect(screen.getByText('Current streak: 1 round')).toBeInTheDocument()
  })

  it('uses the reader’s thousands separator', async () => {
    await i18n.changeLanguage('en')
    render(<RoundReveal result={{ ...mockResultWithCover, baseScore: 1000, roundScore: 1100 }} />)
    expect(screen.getByText('1,100')).toBeInTheDocument()
    expect(screen.getByText('Right at level 5s')).toBeInTheDocument()
  })

  it('describes a lost round and scores zero', () => {
    render(<RoundReveal result={mockResultWrongOut} />)
    expect(screen.getByText('Chưa đoán ra')).toBeInTheDocument()
    expect(screen.getByText('Đoán sai 3 lần')).toBeInTheDocument()
    const rows = new Map(
      screen
        .getAllByRole('term')
        .map((term) => [term.textContent, term.nextElementSibling?.textContent]),
    )
    expect(rows.get('Điểm vòng')).toBe('0')
    expect(rows.get('Combo')).toBe('—không có')
  })

  it('continues, and the button says what comes next', async () => {
    const onContinue = vi.fn()
    const view = render(<RoundReveal result={mockResultWithCover} onContinue={onContinue} />)
    await userEvent.click(screen.getByRole('button', { name: 'Vòng tiếp theo' }))
    expect(onContinue).toHaveBeenCalledOnce()

    view.rerender(
      <RoundReveal result={mockResultWithCover} onContinue={onContinue} matchFinished />,
    )
    expect(screen.getByRole('button', { name: 'Xem kết quả' })).toBeInTheDocument()
  })
})
