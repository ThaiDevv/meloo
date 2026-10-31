import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MOCK_COVER_URL } from '../mocks/fixtures'
import { Packshot } from './Packshot'

describe('Packshot', () => {
  it('puts the cover on the sleeve and, without a second announcement, on the record label', () => {
    const { container } = render(
      <Packshot
        coverUrl={MOCK_COVER_URL}
        title="Chúng Ta Của Hiện Tại"
        artistName="Sơn Tùng M-TP"
        correct
      />,
    )
    expect(screen.getAllByRole('img', { name: /Ảnh bìa bài/ })).toHaveLength(1)
    // The label repeats the image for the eye only.
    expect(container.querySelectorAll(`img[src="${MOCK_COVER_URL}"]`)).toHaveLength(2)
  })

  it('sets the title in type on a plain sleeve when there is no cover', () => {
    render(<Packshot title="Chúng Ta Của Hiện Tại" artistName="Sơn Tùng M-TP" correct={false} />)
    const sleeve = screen.getByRole('img', { name: 'Chưa có ảnh bìa' })
    expect(sleeve).toHaveTextContent('Chúng Ta Của Hiện Tại')
    expect(sleeve).toHaveTextContent('Sơn Tùng M-TP')
    expect(document.querySelectorAll('img')).toHaveLength(0)
  })
})
