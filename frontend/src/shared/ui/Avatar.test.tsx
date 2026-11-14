import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Avatar } from './Avatar'

describe('Avatar', () => {
  it('prefers the saved preset to an OAuth picture', () => {
    const { container } = render(<Avatar name="Meloman" src="/photo.png" avatarId="piano" />)
    expect(container.querySelector('[data-avatar-id="piano"]')).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })
  it('falls back to the initial when the picture fails', () => {
    render(<Avatar name="Meloman" src="/bad.png" />)
    fireEvent.error(screen.getByRole('img', { name: 'Meloman' }))
    expect(screen.getByRole('img', { name: 'Meloman' })).toHaveTextContent('M')
  })
})
