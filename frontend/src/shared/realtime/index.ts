import { Client } from '@stomp/stompjs'

/** Build a STOMP client; connection and message types arrive with CT-02 / FE-ROOM tickets. */
export function createStompClient(
  brokerURL: string = import.meta.env.VITE_WS_URL ?? '/ws',
): Client {
  return new Client({ brokerURL, reconnectDelay: 3000 })
}
