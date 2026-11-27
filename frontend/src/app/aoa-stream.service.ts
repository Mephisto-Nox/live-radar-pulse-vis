import { Injectable, signal } from '@angular/core';
import { AoaRadiusMode, AoaStreamConfiguration, AoaTile } from './aoa-types';

export type AoaConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

const TILE_HEADER_BYTES = 52;
const MAX_VISIBLE_ROWS_PER_RECEIVER = 300;

@Injectable({ providedIn: 'root' })
export class AoaStreamService {
  readonly status = signal<AoaConnectionStatus>('disconnected');
  readonly tiles = signal<AoaTile[]>([]);
  readonly receivers = signal<string[]>([]);
  readonly configuration = signal<AoaStreamConfiguration>({
    receiverIds: [],
    timeWindowSeconds: 10,
    frequencyFromGHz: 0,
    frequencyToGHz: 60,
    amplitudeThresholdDbm: -65,
    minAmplitudeDbm: -80,
    maxAmplitudeDbm: -30,
    radiusMode: 'amplitude',
  });

  private socket?: WebSocket;
  private reconnectTimer?: number;
  private manuallyClosed = false;

  connect(): void {
    this.manuallyClosed = false;
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return;
    this.status.set('connecting');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/aoa`);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.status.set('connected');
      this.sendConfiguration();
    };
    socket.onmessage = (event: MessageEvent<ArrayBuffer | string>) => {
      if (this.socket !== socket) return;
      if (typeof event.data === 'string') this.handleStatus(event.data);
      else this.handleTile(event.data);
    };
    socket.onerror = () => {
      if (this.socket === socket) this.status.set('error');
    };
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = undefined;
      this.status.set('disconnected');
      if (!this.manuallyClosed) this.reconnectTimer = window.setTimeout(() => this.connect(), 2_000);
    };
  }

  disconnect(): void {
    this.manuallyClosed = true;
    window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = undefined;
    const socket = this.socket;
    this.socket = undefined;
    socket?.close();
    this.status.set('disconnected');
  }

  configure(configuration: AoaStreamConfiguration): void {
    this.configuration.set({ ...configuration, receiverIds: [...configuration.receiverIds] });
    this.sendConfiguration();
  }

  private sendConfiguration(): void {
    if (this.socket?.readyState !== WebSocket.OPEN) return;
    this.socket.send(JSON.stringify({ type: 'configure', ...this.configuration() }));
  }

  private handleStatus(message: string): void {
    try {
      const payload: unknown = JSON.parse(message);
      if (typeof payload !== 'object' || payload === null) return;
      const status = payload as { type?: unknown; receivers?: unknown; defaultReceiverId?: unknown };
      const receiverList = status.receivers;
      if (status.type !== 'status' || !Array.isArray(receiverList)
        || !receiverList.every((receiver) => typeof receiver === 'string')) return;
      this.receivers.set(receiverList);
      const current = this.configuration();
      const available = current.receiverIds.filter((receiver) => receiverList.includes(receiver));
      const receiverIds = available.length > 0
        ? available
        : [typeof status.defaultReceiverId === 'string' ? status.defaultReceiverId : receiverList[0] ?? 'RX-01'];
      if (receiverIds.join('\0') !== current.receiverIds.join('\0')) {
        this.configuration.set({ ...current, receiverIds });
        this.sendConfiguration();
      }
    } catch {
      this.status.set('error');
    }
  }

  private handleTile(buffer: ArrayBuffer): void {
    if (buffer.byteLength < TILE_HEADER_BYTES) {
      this.status.set('error');
      return;
    }
    const view = new DataView(buffer);
    if (view.getUint8(0) !== 80 || view.getUint8(1) !== 86 || view.getUint8(2) !== 65
      || view.getUint8(3) !== 79 || view.getUint8(4) !== 1) {
      this.status.set('error');
      return;
    }
    const receiverLength = view.getUint16(6, true);
    const startTimeMs = Number(view.getBigInt64(8, true));
    const durationMs = view.getUint32(16, true);
    const inputPulseCount = view.getUint32(20, true);
    const pulseCount = view.getUint32(24, true);
    const sampleCount = view.getUint16(28, true);
    const angleBinCount = view.getUint16(30, true);
    const radialBinCount = view.getUint16(32, true);
    const radiusMode = radiusModeFromCode(view.getUint8(34));
    const radialMinValue = view.getInt32(36, true);
    const radialMaxValue = view.getInt32(40, true);
    const minFrequencyKHz = view.getUint32(44, true);
    const maxFrequencyKHz = view.getUint32(48, true);
    const receiverOffset = TILE_HEADER_BYTES;
    const countsOffset = receiverOffset + receiverLength;
    const countsLength = angleBinCount * radialBinCount;
    const samplesOffset = countsOffset + countsLength * Uint16Array.BYTES_PER_ELEMENT;
    if (angleBinCount !== 360 || radialBinCount === 0
      || samplesOffset + sampleCount * 8 !== buffer.byteLength) {
      this.status.set('error');
      return;
    }
    const receiverId = new TextDecoder().decode(new Uint8Array(buffer, receiverOffset, receiverLength));
    const counts = new Uint16Array(countsLength);
    for (let index = 0; index < countsLength; index++) counts[index] = view.getUint16(countsOffset + index * 2, true);
    const samples = Array.from({ length: sampleCount }, (_, index) => {
      const offset = samplesOffset + index * 8;
      return {
        angleHundredthDegree: view.getUint16(offset, true),
        amplitudeTenthDbm: view.getInt16(offset + 2, true),
        frequencyKHz: view.getUint32(offset + 4, true),
      };
    });
    const tile: AoaTile = {
      receiverId,
      startTimeMs,
      durationMs,
      inputPulseCount,
      pulseCount,
      angleBinCount,
      radialBinCount,
      radiusMode,
      radialMinValue,
      radialMaxValue,
      minFrequencyKHz,
      maxFrequencyKHz,
      gap: (view.getUint8(5) & 1) !== 0,
      counts,
      samples,
    };
    this.tiles.update((tiles) => {
      const index = tiles.findIndex((existing) => existing.receiverId === receiverId && existing.startTimeMs === startTimeMs);
      const updated = index < 0 ? [...tiles, tile] : tiles.map((existing, current) => current === index ? tile : existing);
      const receiverRows = updated.filter((row) => row.receiverId === receiverId).slice(-MAX_VISIBLE_ROWS_PER_RECEIVER);
      const retained = updated.filter((row) => row.receiverId !== receiverId);
      return [...retained, ...receiverRows].sort((left, right) => left.startTimeMs - right.startTimeMs);
    });
  }
}

function radiusModeFromCode(code: number): AoaRadiusMode {
  if (code === 1) return 'frequency';
  if (code === 2) return 'age';
  return 'amplitude';
}
