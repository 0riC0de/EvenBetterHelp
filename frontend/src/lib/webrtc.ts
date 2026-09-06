import type { Signal } from './types';

/** Browser leg only. A separately deployed media gateway must translate Cobalt's native media. */
export class CallConnection {
  private peer: RTCPeerConnection | null = null;
  private local: MediaStream | null = null;
  private pendingIce: RTCIceCandidateInit[] = [];
  constructor(private send: (signal: Signal) => void, private id: string, private onRemote: (stream: MediaStream) => void, private onState: (state: RTCPeerConnectionState) => void) {}
  async open(video: boolean, iceServers: RTCIceServer[]) {
    this.local = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true }, video });
    this.peer = new RTCPeerConnection({ iceServers });
    this.local.getTracks().forEach((track) => this.peer!.addTrack(track, this.local!));
    this.peer.ontrack = (event) => this.onRemote(event.streams[0] ?? new MediaStream([event.track]));
    this.peer.onconnectionstatechange = () => this.onState(this.peer!.connectionState);
    this.peer.onicecandidate = (event) => { if (event.candidate) this.emit('ice', event.candidate.toJSON()); };
    return this.local;
  }
  async offer() {
    if (!this.peer) throw new Error('Call is not initialized.');
    const offer = await this.peer.createOffer(); await this.peer.setLocalDescription(offer); this.emit('offer', offer);
  }
  async receive(signal: Signal) {
    if (!this.peer || signal.callId !== this.id) return;
    const handlers: Record<string, () => Promise<void>> = {
      ice: () => this.addIce(JSON.parse(signal.payload)),
      answer: () => this.description(JSON.parse(signal.payload)),
      offer: async () => { await this.description(JSON.parse(signal.payload)); const answer = await this.peer!.createAnswer(); await this.peer!.setLocalDescription(answer); this.emit('answer', answer); },
      end: async () => { this.close(); }, decline: async () => { this.close(); },
    };
    await handlers[signal.type]?.();
  }
  private async description(description: RTCSessionDescriptionInit) {
    await this.peer!.setRemoteDescription(description);
    for (const candidate of this.pendingIce) await this.peer!.addIceCandidate(candidate);
    this.pendingIce = [];
  }
  private async addIce(candidate: RTCIceCandidateInit) {
    if (!this.peer?.remoteDescription) { this.pendingIce.push(candidate); return; }
    await this.peer.addIceCandidate(candidate);
  }
  mute(muted: boolean) { this.local?.getAudioTracks().forEach((track) => { track.enabled = !muted; }); }
  camera(enabled: boolean) { this.local?.getVideoTracks().forEach((track) => { track.enabled = enabled; }); }
  async switchCamera() {
    const old = this.local?.getVideoTracks()[0];
    if (!old || !this.peer) return;
    const facingMode = old.getSettings().facingMode === 'environment' ? 'user' : 'environment';
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode } });
    const track = stream.getVideoTracks()[0];
    await this.peer.getSenders().find((sender) => sender.track?.kind === 'video')?.replaceTrack(track);
    old.stop(); this.local!.removeTrack(old); this.local!.addTrack(track);
  }
  close() { this.local?.getTracks().forEach((track) => track.stop()); this.peer?.close(); this.peer = null; this.local = null; this.pendingIce = []; }
  private emit(type: string, payload: unknown) { this.send({ callId: this.id, type, payload: JSON.stringify(payload) }); }
}
