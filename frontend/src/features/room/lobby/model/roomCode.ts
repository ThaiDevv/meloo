/**
 * Pattern of the contract (JoinRoomRequest.code). BR-11 also drops "L" from the alphabet, but the
 * contract and its own example (MEL889) accept it, so the screen does not reject what the server accepts.
 */
export const ROOM_CODE_PATTERN = /^[A-HJ-NP-Z2-9]{6}$/

/**
 * Turns what a person typed or pasted into a room code: "mel 889", "MEL889" and
 * "https://meloo.app/r/mel889?x=1" all give "MEL889". Null when no valid code can be read.
 */
export function readRoomCode(input: string): string | null {
  const text = input.trim()
  const fromLink = /\/r\/([^/?#\s]+)/i.exec(text)?.[1]
  const candidate = (fromLink ?? text).replace(/[\s-]/g, '').toUpperCase()
  return ROOM_CODE_PATTERN.test(candidate) ? candidate : null
}

/** The invitation link for a room code, on the origin the player is using. */
export function inviteLink(code: string, origin: string = location.origin): string {
  return `${origin}/r/${code}`
}
