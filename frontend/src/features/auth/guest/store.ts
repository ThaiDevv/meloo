import { create } from 'zustand'
import type { AvatarId } from './avatars'

interface AvatarState {
  /**
   * Client-side only: the contract has no avatar field yet, so it is not sent to the server and is
   * lost on reload. Memory only, like the access token.
   */
  avatarId: AvatarId | null
  setAvatarId: (avatarId: AvatarId | null) => void
}

export const useAvatarStore = create<AvatarState>((set) => ({
  avatarId: null,
  setAvatarId: (avatarId) => {
    set({ avatarId })
  },
}))
