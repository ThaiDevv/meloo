import { describe, expect, it } from 'vitest'
import { inviteLink, readRoomCode } from './roomCode'

describe('readRoomCode', () => {
  it.each([
    ['MEL889', 'MEL889'],
    ['mel889', 'MEL889'],
    ['  mel 889  ', 'MEL889'],
    ['MEL-889', 'MEL889'],
    ['https://meloo.app/r/mel889', 'MEL889'],
    ['https://meloo.app/r/MEL889?utm=x#top', 'MEL889'],
    ['meloo.app/r/ab2cde', 'AB2CDE'],
  ])('reads %j as %s', (input, code) => {
    expect(readRoomCode(input)).toBe(code)
  })

  it.each([
    ['empty', ''],
    ['too short', 'ABC12'],
    ['too long', 'ABCDEFG'],
    ['a letter the alphabet leaves out (O)', 'ABCDEO'],
    ['a digit the alphabet leaves out (1)', 'ABCDE1'],
    ['a link without a code', 'https://meloo.app/solo'],
    ['symbols', 'AB!DEF'],
  ])('rejects %s', (_name, input) => {
    expect(readRoomCode(input)).toBeNull()
  })
})

describe('inviteLink', () => {
  it('points at /r/<code> on the given origin', () => {
    expect(inviteLink('MEL889', 'https://meloo.app')).toBe('https://meloo.app/r/MEL889')
  })
})
