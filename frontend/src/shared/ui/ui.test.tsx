import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { useThemeStore } from '@/shared/theme/store'
import { Avatar } from './Avatar'
import { Dialog } from './Dialog'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'
import { Tabs, TabsContent, TabsList, TabsTrigger } from './Tabs'
import { ThemeToggle } from './ThemeToggle'
import { Toaster } from './Toast'
import { toast, useToastStore } from './toast-store'

describe('Dialog', () => {
  beforeAll(() => {
    // jsdom has no modal <dialog>; mimic what the browser does with the `open` attribute.
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }
  })

  it('opens as a modal named by its title and described by its text', () => {
    render(<Dialog open onClose={vi.fn()} title="Rời phòng?" description="Bạn sẽ mất lượt chơi." />)
    const dialog = screen.getByRole('dialog', { name: 'Rời phòng?' })
    expect(dialog).toHaveAccessibleDescription('Bạn sẽ mất lượt chơi.')
  })

  it('closes from the close button and from a click on the backdrop', async () => {
    const onClose = vi.fn()
    render(<Dialog open onClose={onClose} title="Rời phòng?" />)

    await userEvent.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(onClose).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('dialog'))
    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('stays closed until `open` is true', () => {
    render(<Dialog open={false} onClose={vi.fn()} title="Rời phòng?" />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('Tabs', () => {
  function renderTabs() {
    render(
      <Tabs defaultValue="a">
        <TabsList aria-label="Chế độ">
          <TabsTrigger value="a">Một</TabsTrigger>
          <TabsTrigger value="b">Hai</TabsTrigger>
          <TabsTrigger value="c">Ba</TabsTrigger>
        </TabsList>
        <TabsContent value="a">Nội dung một</TabsContent>
        <TabsContent value="b">Nội dung hai</TabsContent>
        <TabsContent value="c">Nội dung ba</TabsContent>
      </Tabs>,
    )
  }

  it('shows only the selected panel, labelled by its tab', async () => {
    renderTabs()
    expect(screen.getByRole('tab', { name: 'Một' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Một')

    await userEvent.click(screen.getByRole('tab', { name: 'Hai' }))
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Nội dung hai')
    expect(screen.queryByText('Nội dung một')).not.toBeInTheDocument()
  })

  it('moves with the arrow keys, Home and End, and wraps around', async () => {
    renderTabs()
    screen.getByRole('tab', { name: 'Một' }).focus()

    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Hai' })).toHaveFocus()
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Nội dung hai')

    await userEvent.keyboard('{End}')
    expect(screen.getByRole('tab', { name: 'Ba' })).toHaveAttribute('aria-selected', 'true')

    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Một' })).toHaveFocus()

    await userEvent.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Ba' })).toHaveFocus()
  })

  it('keeps only the selected tab in the tab order', () => {
    renderTabs()
    expect(screen.getByRole('tab', { name: 'Một' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: 'Hai' })).toHaveAttribute('tabindex', '-1')
  })
})

describe('Toast', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('announces a toast in a polite live region and dismisses it on request', async () => {
    render(<Toaster />)
    act(() => {
      toast({ title: 'Đã sao chép mã phòng', tone: 'positive' })
    })
    expect(screen.getByText('Đã sao chép mã phòng')).toBeInTheDocument()
    expect(screen.getByRole('list')).toHaveAttribute('aria-live', 'polite')

    await userEvent.click(screen.getByRole('button', { name: 'Ẩn thông báo' }))
    expect(screen.queryByText('Đã sao chép mã phòng')).not.toBeInTheDocument()
  })

  it('goes away by itself after 5 seconds', () => {
    vi.useFakeTimers()
    render(<Toaster />)
    act(() => {
      toast({ title: 'Mất kết nối', description: 'Đang thử kết nối lại.', tone: 'negative' })
    })
    expect(screen.getByText('Đang thử kết nối lại.')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(screen.queryByText('Mất kết nối')).not.toBeInTheDocument()
  })
})

describe('Avatar', () => {
  it('shows the first letter, named by the person, when there is no picture', () => {
    render(<Avatar name="quốc Bảo" />)
    expect(screen.getByRole('img', { name: 'quốc Bảo' })).toHaveTextContent('Q')
  })

  it('falls back to the initial when the picture fails to load', () => {
    render(<Avatar name="Linh" src="/broken.png" />)
    fireEvent.error(screen.getByRole('img', { name: 'Linh' }))
    expect(screen.getByRole('img', { name: 'Linh' })).toHaveTextContent('L')
  })
})

describe('EmptyState and ErrorState', () => {
  it('EmptyState shows its sentence and its next step', () => {
    render(
      <EmptyState
        title="Chưa có trận nào"
        description="Chơi một ván."
        action={<button>Chơi đơn</button>}
      />,
    )
    expect(screen.getByText('Chưa có trận nào')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chơi đơn' })).toBeInTheDocument()
  })

  it('ErrorState is an alert with default words and a retry button', async () => {
    const onRetry = vi.fn()
    render(<ErrorState onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Có lỗi xảy ra')

    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('ErrorState has no retry button when there is nothing to retry', () => {
    render(<ErrorState />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('ThemeToggle', () => {
  beforeEach(() => {
    localStorage.clear()
    useThemeStore.getState().setTheme('dark')
  })

  it('names the theme it switches to and flips <html>', async () => {
    render(<ThemeToggle />)
    await userEvent.click(screen.getByRole('button', { name: 'Chuyển sang giao diện sáng' }))
    expect(document.documentElement).not.toHaveClass('dark')

    await userEvent.click(screen.getByRole('button', { name: 'Chuyển sang giao diện tối' }))
    expect(document.documentElement).toHaveClass('dark')
  })
})
