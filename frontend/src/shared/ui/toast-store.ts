import { create } from 'zustand'

export type ToastTone = 'neutral' | 'positive' | 'negative'

export interface ToastItem {
  id: number
  title: string
  description?: string
  tone: ToastTone
}

interface ToastState {
  toasts: ToastItem[]
  push: (toast: Omit<ToastItem, 'id' | 'tone'> & { tone?: ToastTone }) => number
  dismiss: (id: number) => void
}

let nextId = 1

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: ({ tone = 'neutral', ...toast }) => {
    const id = nextId++
    set((s) => ({ toasts: [...s.toasts, { ...toast, tone, id }] }))
    return id
  },
  dismiss: (id) => {
    set((s) => ({ toasts: s.toasts.filter((x) => x.id !== id) }))
  },
}))

/** Shows a toast from anywhere (event handlers, mutations). <Toaster /> in the layout renders it. */
export function toast(input: Parameters<ToastState['push']>[0]): number {
  return useToastStore.getState().push(input)
}
