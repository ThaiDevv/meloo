import { effectiveMic } from './mute.js';
const $ = id => document.getElementById(id);
const id = crypto.randomUUID(), peers = new Map();
let socket, stream, wanted = false, destination, musicUrl;
const log = value => { $('output').textContent += `${value}\n`; };
function updateMic() {
  const playing = !$('player').paused && !$('player').ended;
  if (stream) stream.getAudioTracks().forEach(track => { track.enabled = effectiveMic(wanted, playing); });
  $('mic').textContent = wanted ? 'Tắt mic' : 'Bật mic';
  $('status').textContent = wanted && playing ? 'Mic tạm tắt khi phát nhạc' : wanted ? 'Mic bật' : 'Mic tắt';
}
function signal(type, to, payload = {}) {
  socket.send(`SEND\ndestination:${destination}\ncontent-type:application/json\n\n${JSON.stringify({ from: id, to, type, ...payload })}\0`);
}
function peer(remote) {
  if (peers.has(remote)) return peers.get(remote);
  const pc = new RTCPeerConnection({ iceServers: $('ice').value ? JSON.parse($('ice').value) : [{ urls: 'stun:stun.l.google.com:19302' }] });
  const state = { pc, candidates: [] }; peers.set(remote, state);
  stream.getTracks().forEach(track => pc.addTrack(track, stream));
  pc.onicecandidate = e => { if (e.candidate) signal('candidate', remote, { candidate: e.candidate }); };
  pc.ontrack = e => { let audio = document.getElementById(remote); if (!audio) { audio = document.createElement('audio'); audio.id = remote; audio.autoplay = true; audio.controls = true; $('audio').append(audio); } audio.srcObject = e.streams[0]; };
  pc.onconnectionstatechange = () => log(`${remote.slice(0, 8)}: ${pc.connectionState}`);
  return state;
}
async function receive(message) {
  if (message.from === id || (message.to && message.to !== id)) return;
  if (message.type === 'leave') { peers.get(message.from)?.pc.close(); peers.delete(message.from); document.getElementById(message.from)?.remove(); return; }
  const state = peer(message.from), pc = state.pc;
  if (message.type === 'hello') { signal('present', message.from); return; }
  if (message.type === 'present') { await pc.setLocalDescription(await pc.createOffer()); signal('offer', message.from, { description: pc.localDescription }); return; }
  if (message.type === 'offer' || message.type === 'answer') {
    await pc.setRemoteDescription(message.description);
    for (const candidate of state.candidates.splice(0)) await pc.addIceCandidate(candidate);
    if (message.type === 'offer') { await pc.setLocalDescription(await pc.createAnswer()); signal('answer', message.from, { description: pc.localDescription }); }
  } else if (message.type === 'candidate') {
    if (pc.remoteDescription) await pc.addIceCandidate(message.candidate); else state.candidates.push(message.candidate);
  }
}
$('join').onclick = async () => {
  try {
    if (!/^[a-zA-Z0-9-]{1,40}$/.test($('room').value)) throw Error('Mã phòng không hợp lệ');
    if ($('ice').value) JSON.parse($('ice').value);
    stream = await navigator.mediaDevices.getUserMedia({ audio: true }); updateMic();
    destination = `/topic/voice/${$('room').value}`;
    socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/stomp`);
    socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
    socket.onmessage = e => {
      for (const frame of e.data.split('\0')) {
        if (frame.startsWith('CONNECTED')) { socket.send(`SUBSCRIBE\nid:voice\ndestination:${destination}\n\n\0`); signal('hello', null); $('join').disabled = true; $('mic').disabled = false; $('stats').disabled = false; }
        if (frame.startsWith('MESSAGE\n')) { try { const body = frame.slice(frame.indexOf('\n\n') + 2); receive(JSON.parse(body)).catch(err => log(`Lỗi signaling: ${err.message}`)); } catch (err) { log(`Lỗi dữ liệu: ${err.message}`); } }
      }
    };
    socket.onclose = () => { stream.getTracks().forEach(track => track.stop()); for (const { pc } of peers.values()) pc.close(); $('mic').disabled = true; $('status').textContent = 'Đã ngắt kết nối'; };
    socket.onerror = () => log('Không kết nối được signaling');
  } catch (err) { log(`Không thể tham gia: ${err.message}`); }
};
$('mic').onclick = () => { wanted = !wanted; updateMic(); };
$('music').onchange = e => { if (musicUrl) URL.revokeObjectURL(musicUrl); const file = e.target.files[0]; if (file) { musicUrl = URL.createObjectURL(file); $('player').src = musicUrl; } };
for (const event of ['play', 'pause', 'ended', 'emptied']) $('player').addEventListener(event, updateMic);
$('stats').onclick = async () => { for (const [remote, { pc }] of peers) { const stats = await pc.getStats(); for (const report of stats.values()) if (report.type === 'transport' && report.selectedCandidatePairId) { const pair = stats.get(report.selectedCandidatePairId), local = stats.get(pair.localCandidateId), other = stats.get(pair.remoteCandidateId); log(`${remote.slice(0, 8)}: ${local?.candidateType}/${other?.candidateType}; bytes gửi=${pair.bytesSent}, nhận=${pair.bytesReceived}; thời điểm=${new Date().toISOString()}`); } } };
window.addEventListener('beforeunload', () => { if (socket?.readyState === WebSocket.OPEN) signal('leave', null); });
