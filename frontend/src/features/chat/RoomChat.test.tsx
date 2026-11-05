import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import { installFakeStomp, type FakeStompKit } from '@/shared/realtime/testing/fakeStomp'
import { ROOM_ID } from '@/shared/realtime/testing/fixtures'
import message from '../../../../contracts/examples/room/event.chat.message.json'
import snapshot from '../../../../contracts/examples/room/queue.resync-state.json'
import blocked from '../../../../contracts/examples/room/queue.error.json'
import { RoomChat } from './index'
import { useChatStore } from './store'

let kit: FakeStompKit
const topic = `/topic/rooms/${ROOM_ID}/chat`
function live() {
  act(() => {
    kit.latest().connect()
    kit
      .latest()
      .emit('/user/queue/private', { ...snapshot, room: { ...snapshot.room, roomId: ROOM_ID } })
  })
}
function emit(content = message.content, messageId = message.messageId) {
  act(() => {
    kit.latest().emit(topic, { ...message, content, messageId })
  })
}
function Parent() {
  useRoomRealtime(ROOM_ID)
  return <RoomChat roomId={ROOM_ID} />
}
beforeEach(() => {
  kit = installFakeStomp()
  useChatStore.setState({ roomId: null, messages: [] })
  useRoomStore.getState().reset()
})
afterEach(() => {
  kit.uninstall()
})

describe('RoomChat', () => {
  it('shares the parent connection, escapes HTML and scrolls on incoming messages', () => {
    render(<Parent />)
    expect(kit.clients).toHaveLength(1)
    live()
    const log = screen.getByRole('log')
    Object.defineProperty(log, 'scrollHeight', { configurable: true, value: 900 })
    emit('<img src=x onerror=alert(1)>')
    expect(within(log).getByText('<img src=x onerror=alert(1)>')).toBeVisible()
    expect(log.querySelector('img')).toBeNull()
    expect(log.scrollTop).toBe(900)
    expect(screen.getByText(message.senderNickname)).toBeVisible()
    expect(log.querySelector('time')).toHaveAttribute('datetime', message.sentAt)
  })

  it('limits input to 200, rejects whitespace and only displays server-confirmed messages', async () => {
    const user = userEvent.setup()
    render(<RoomChat roomId={ROOM_ID} />)
    live()
    const input = screen.getByRole('textbox', { name: 'Tin nhắn' })
    const send = screen.getByRole('button', { name: 'Gửi' })
    await user.type(input, '   ')
    expect(send).toBeDisabled()
    await user.clear(input)
    await user.type(input, 'x'.repeat(201))
    expect(input).toHaveValue('x'.repeat(200))
    await user.keyboard('{Enter}')
    expect(kit.latest().commands('chat')).toEqual([{ content: 'x'.repeat(200) }])
    expect(input).toHaveValue('')
    expect(screen.getByText(i18n.t('chat:empty'))).toBeVisible()
    fireEvent.change(input, { target: { value: 'x'.repeat(201) } })
    fireEvent.submit(input.closest('form') ?? input)
    expect(kit.latest().commands('chat')).toHaveLength(1)
  })

  it.each(['CHAT_ANSWER_LEAK', 'CHAT_PROHIBITED_WORD', 'RATE_LIMITED'])(
    'localizes private %s without rendering server diagnostics or broadcasting rejected text',
    async (code) => {
      render(<RoomChat roomId={ROOM_ID} />)
      live()
      act(() => {
        kit
          .latest()
          .emit('/user/queue/errors', { ...blocked, code, message: 'SECRET', detail: 'ANSWER' })
      })
      expect(screen.getByRole('alert')).toHaveTextContent(i18n.t(`chat:errors.${code}`))
      expect(screen.queryByText(/SECRET|ANSWER/)).not.toBeInTheDocument()
      expect(screen.getByRole('log').querySelector('article')).toBeNull()
      await userEvent.click(screen.getByRole('button', { name: 'Đóng cảnh báo' }))
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    },
  )

  it('disables sending while disconnected and resyncing, preserves the draft and resumes after resync', async () => {
    render(<RoomChat roomId={ROOM_ID} />)
    const input = screen.getByRole('textbox')
    await userEvent.type(input, 'Xin chào')
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    live()
    act(() => {
      kit.latest().drop()
    })
    expect(input).toHaveValue('Xin chào')
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    act(() => {
      kit.latest().connect()
    })
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    live()
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }))
    expect(kit.latest().commands('chat')).toEqual([{ content: 'Xin chào' }])
    emit()
    expect(screen.getByText(message.content)).toBeVisible()
  })

  it('keeps a draft if the socket closes just before publish', async () => {
    render(<RoomChat roomId={ROOM_ID} />)
    live()
    await userEvent.type(screen.getByRole('textbox'), 'Chào cả phòng')
    kit.latest().connected = false
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }))
    expect(screen.getByRole('alert')).toHaveTextContent(i18n.t('chat:sendFailed'))
    expect(screen.getByRole('textbox')).toHaveValue('Chào cả phòng')
    expect(kit.latest().commands('chat')).toEqual([])
  })

  it('retains messages across remounts, deduplicates reconnects and clears another room', () => {
    const first = render(<RoomChat roomId={ROOM_ID} />)
    live()
    emit()
    first.unmount()
    const next = render(<RoomChat roomId={ROOM_ID} />)
    emit()
    expect(screen.getByRole('log').querySelectorAll('article')).toHaveLength(1)
    next.rerender(<RoomChat roomId="another-room" />)
    expect(screen.queryByText(message.content)).not.toBeInTheDocument()
    kit.clients[0]?.emit(topic, message)
    expect(screen.queryByText(message.content)).not.toBeInTheDocument()
  })

  it('has English translations and explains why reporting is disabled (AC3 deferred by PO)', async () => {
    render(<RoomChat roomId={ROOM_ID} />)
    live()
    emit()
    await act(async () => {
      await i18n.changeLanguage('en')
    })
    expect(screen.getByRole('heading', { name: 'Room chat' })).toBeVisible()
    const report = screen.getByRole('button', {
      name: `Report message from ${message.senderNickname}`,
    })
    expect(report).toBeDisabled()
    expect(report).toHaveAccessibleDescription('Message reporting is not available yet.')
    expect(screen.getByRole('button', { name: 'Send' })).toBeVisible()
    expect(kit.latest().commands('report')).toEqual([])
  })
})
