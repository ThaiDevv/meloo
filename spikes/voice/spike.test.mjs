import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { effectiveMic } from './mute.js';

test('BR-19: mic follows user intent except during playback', () => {
  assert.equal(effectiveMic(false, false), false);
  assert.equal(effectiveMic(false, true), false);
  assert.equal(effectiveMic(true, true), false);
  assert.equal(effectiveMic(true, false), true);
});

test('STOMP relay forwards within room to other subscribers', async () => {
  const port = 18000 + Math.floor(Math.random() * 10000);
  const server = spawn(process.execPath, ['server.mjs'], { cwd: new URL('.', import.meta.url), env: { ...process.env, PORT: String(port) } });
  try {
    await Promise.race([once(server.stdout, 'data'), once(server, 'exit').then(() => { throw Error('server exited'); })]);
    async function connect(room) {
      const ws = new WebSocket(`ws://127.0.0.1:${port}/stomp`);
      await once(ws, 'open');
      const ready = once(ws, 'message'); ws.send('CONNECT\naccept-version:1.2\n\n\0'); await ready;
      ws.send(`SUBSCRIBE\nid:one\ndestination:/topic/voice/${room}\n\n\0`);
      return ws;
    }
    const a = await connect('room'); const b = await connect('room'); const c = await connect('room');
    try {
      const messages = [once(b, 'message'), once(c, 'message')];
      a.send('SEND\ndestination:/topic/voice/room\n\n{"type":"hello"}\0');
      for (const [event] of await Promise.all(messages)) assert.match(event.data, /"type":"hello"/);
    } finally { a.close(); b.close(); c.close(); }
  } finally { server.kill(); }
});
