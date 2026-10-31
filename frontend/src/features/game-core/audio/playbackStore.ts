import { create } from 'zustand'

interface PlaybackState {
  playing: boolean
}

const usePlaybackStore = create<PlaybackState>(() => ({ playing: false }))

/** Written only by the game screen's player. */
export function setSnippetPlaying(playing: boolean): void {
  usePlaybackStore.setState({ playing })
}

/**
 * True while a round's clip is audible. Voice chat (FE-RTC-01) mutes the mic while this is true
 * and turns it back on when it goes false (BR-19).
 */
export function useSnippetPlaying(): boolean {
  return usePlaybackStore((state) => state.playing)
}

/** For code outside React (e.g. a STOMP or WebRTC handler). */
export function isSnippetPlaying(): boolean {
  return usePlaybackStore.getState().playing
}
