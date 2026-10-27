import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { UiGalleryPage } from './UiGalleryPage'

describe('UiGalleryPage', () => {
  it('lists every shared component under its own heading', () => {
    render(<UiGalleryPage />)
    expect(
      screen.getByRole('heading', { level: 1, name: 'Thư viện giao diện' }),
    ).toBeInTheDocument()
    for (const name of [
      'Màu',
      'Nút',
      'Ô nhập',
      'Thẻ và huy hiệu',
      'Ảnh đại diện',
      'Tab',
      'Hộp thoại',
      'Thông báo',
      'Đang tải',
      'Trạng thái rỗng',
      'Trạng thái lỗi',
    ]) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
  })
})
