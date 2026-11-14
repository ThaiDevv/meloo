// Isolated STOMP/WebSocket relay for local experiments only; no authentication.
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('.', import.meta.url));
const clients = new Set();
const server = createServer(async (req, res) => {
  const files = { '/': ['index.html', 'text/html'], '/client.js': ['client.js', 'text/javascript'], '/mute.js': ['mute.js', 'text/javascript'] };
  const file = files[req.url];
  if (!file) { res.writeHead(404).end(); return; }
  res.writeHead(200, { 'Content-Type': file[1], 'Cache-Control': 'no-store' });
  res.end(await readFile(root + file[0]));
});
function send(socket, payload, opcode = 1) {
  const data = Buffer.from(payload);
  const header = data.length < 126 ? Buffer.from([0x80 | opcode, data.length]) : Buffer.from([0x80 | opcode, 126, data.length >> 8, data.length & 255]);
  socket.write(Buffer.concat([header, data]));
}
server.on('upgrade', (req, socket) => {
  if (req.url !== '/stomp' || !req.headers['sec-websocket-key']) { socket.destroy(); return; }
  const accept = createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
  const client = { socket, buffer: Buffer.alloc(0), subscription: null }; clients.add(client);
  socket.on('close', () => clients.delete(client)); socket.on('error', () => clients.delete(client));
  socket.on('data', chunk => {
    client.buffer = Buffer.concat([client.buffer, chunk]);
    while (client.buffer.length >= 2) {
      const b = client.buffer; let len = b[1] & 127; let offset = 2;
      if (!(b[0] & 128) || !(b[1] & 128) || len === 127) { socket.destroy(); return; }
      if (len === 126) { if (b.length < 4) return; len = b.readUInt16BE(2); offset = 4; }
      if (b.length < offset + 4 + len) return;
      const mask = b.subarray(offset, offset + 4); const data = Buffer.from(b.subarray(offset + 4, offset + 4 + len));
      for (let i = 0; i < len; i++) data[i] ^= mask[i % 4];
      client.buffer = b.subarray(offset + 4 + len);
      const opcode = b[0] & 15;
      if (opcode === 8) { socket.end(); return; }
      if (opcode === 9) { send(socket, data, 10); continue; }
      if (opcode !== 1) continue;
      for (const frame of data.toString().split('\0').filter(Boolean)) {
        const [head, ...body] = frame.replace(/^\n+/, '').split('\n\n');
        const [command, ...lines] = head.split('\n');
        const headers = Object.fromEntries(lines.map(line => { const i = line.indexOf(':'); return [line.slice(0, i), line.slice(i + 1)]; }));
        if (command === 'CONNECT' || command === 'STOMP') send(socket, 'CONNECTED\nversion:1.2\n\n\0');
        if (command === 'SUBSCRIBE' && /^\/topic\/voice\/[a-zA-Z0-9-]{1,40}$/.test(headers.destination)) client.subscription = { destination: headers.destination, id: headers.id };
        if (command === 'SEND' && client.subscription && headers.destination === client.subscription.destination && body.join('\n\n').length < 32000) {
          for (const peer of clients) if (peer !== client && peer.subscription?.destination === headers.destination) {
            send(peer.socket, `MESSAGE\nsubscription:${peer.subscription.id}\nmessage-id:1\ndestination:${headers.destination}\ncontent-type:application/json\n\n${body.join('\n\n')}\0`);
          }
        }
      }
    }
  });
});
server.listen(Number(process.env.PORT || 8088), process.env.HOST || '127.0.0.1', () => console.log('Voice spike listening'));
