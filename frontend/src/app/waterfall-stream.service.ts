import { Injectable, signal } from '@angular/core';

export interface WaterfallRow {
  receiverId: string;
  startTimeMs: number;
  durationMs: number;
  inputPulseCount: number;
  frequencyBinCount: number;
  amplitudeBinCount: number;
  minFrequencyKHz: number;
  maxFrequencyKHz: number;
  minPowerTenthDbm: number;
  maxPowerTenthDbm: number;
  gap: boolean;
  counts: Uint16Array;
}

export type WaterfallConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

const MAX_VISIBLE_ROWS = 300;
const TILE_HEADER_BYTES = 44;

@Injectable({ providedIn: 'root' })
export class WaterfallStreamService {
  readonly status = signal<WaterfallConnectionStatus>('disconnected');
  readonly rows = signal<WaterfallRow[]>([]);
  readonly receivers = signal<string[]>(['RX-01']);
  readonly selectedReceiver = signal('RX-01');
  readonly timeWindowSeconds = signal(10);
  readonly udpStatus = signal<'UP' | 'DOWN' | 'UNKNOWN'>('UNKNOWN');
  readonly kafkaStatus = signal<'UP' | 'DOWN' | 'UNKNOWN'>('UNKNOWN');

  private socket?: WebSocket;
  private reconnectTimer?: number;
  private healthTimer?: number;
  private manuallyClosed = false;

  connect(): void {
    this.manuallyClosed = false;
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) {
      return;
    }

    this.status.set('connecting');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/waterfall`);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.status.set('connected');
      this.sendConfiguration();
    };
    socket.onmessage = (event: MessageEvent<ArrayBuffer | string>) => {
      if (this.socket !== socket) return;
      if (typeof event.data === 'string') {
        this.handleStatus(event.data);
      } else {
        this.handleTile(event.data);
      }
    };
    socket.onerror = () => {
      if (this.socket === socket) this.status.set('error');
    };
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = undefined;
      this.status.set('disconnected');
      if (!this.manuallyClosed) {
        this.reconnectTimer = window.setTimeout(() => this.connect(), 2_000);
      }
    };

    this.refreshHealth();
    this.healthTimer ??= window.setInterval(() => this.refreshHealth(), 2_000);
  }

  disconnect(): void {
    this.manuallyClosed = true;
    window.clearTimeout(this.reconnectTimer);
    window.clearInterval(this.healthTimer);
    this.reconnectTimer = undefined;
    this.healthTimer = undefined;
    const socket = this.socket;
    this.socket = undefined;
    socket?.close();
    this.status.set('disconnected');
  }

  configure(receiverId: string, timeWindowSeconds: number): void {
    this.selectedReceiver.set(receiverId);
    this.timeWindowSeconds.set(timeWindowSeconds);
    this.sendConfiguration();
  }

  clearRows(): void {
    this.rows.set([]);
  }

  private sendConfiguration(): void {
    if (this.socket?.readyState !== WebSocket.OPEN) return;
    this.socket.send(JSON.stringify({
      type: 'configure',
      receiverId: this.selectedReceiver(),
      timeWindowSeconds: this.timeWindowSeconds(),
    }));
  }

  private handleStatus(message: string): void {
    try {
      const status: unknown = JSON.parse(message);
      if (!isStatusMessage(status)) return;
      const receiverIds = status.receivers.length > 0 ? status.receivers : [status.defaultReceiverId || 'RX-01'];
      this.receivers.set(receiverIds);
      if (!receiverIds.includes(this.selectedReceiver())) {
        const selected = receiverIds[0] ?? status.defaultReceiverId ?? 'RX-01';
        this.selectedReceiver.set(selected);
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
    if (view.getUint8(0) !== 80 || view.getUint8(1) !== 86 || view.getUint8(2) !== 87 || view.getUint8(3) !== 70
      || view.getUint8(4) !== 1) {
      this.status.set('error');
      return;
    }
    const flags = view.getUint8(5);
    const receiverIdLength = view.getUint16(6, true);
    const startTimeMs = Number(view.getBigInt64(12, true));
    const durationMs = view.getUint32(20, true);
    const inputPulseCount = view.getUint32(24, true);
    const frequencyBinCount = view.getUint16(28, true);
    const amplitudeBinCount = view.getUint16(30, true);
    const minFrequencyKHz = view.getUint32(32, true);
    const maxFrequencyKHz = view.getUint32(36, true);
    const minPowerTenthDbm = view.getInt16(40, true);
    const maxPowerTenthDbm = view.getInt16(42, true);
    const receiverOffset = TILE_HEADER_BYTES;
    const countsOffset = receiverOffset + receiverIdLength;
    const countLength = frequencyBinCount * amplitudeBinCount;
    if (countsOffset + countLength * 2 !== buffer.byteLength || countLength === 0) {
      this.status.set('error');
      return;
    }

    const receiverId = new TextDecoder().decode(new Uint8Array(buffer, receiverOffset, receiverIdLength));
    const counts = new Uint16Array(countLength);
    for (let index = 0; index < countLength; index++) {
      counts[index] = view.getUint16(countsOffset + index * 2, true);
    }
    const row: WaterfallRow = {
      receiverId,
      startTimeMs,
      durationMs,
      inputPulseCount,
      frequencyBinCount,
      amplitudeBinCount,
      minFrequencyKHz,
      maxFrequencyKHz,
      minPowerTenthDbm,
      maxPowerTenthDbm,
      gap: (flags & 1) !== 0,
      counts,
    };
    this.rows.update((rows) => {
      const existingIndex = rows.findIndex(
        (existing) => existing.receiverId === row.receiverId && existing.startTimeMs === row.startTimeMs,
      );
      if (existingIndex < 0) {
        return [...rows, row]
          .sort((left, right) => left.startTimeMs - right.startTimeMs)
          .slice(-MAX_VISIBLE_ROWS);
      }
      const updatedRows = [...rows];
      updatedRows[existingIndex] = row;
      return updatedRows;
    });
  }

  private async refreshHealth(): Promise<void> {
    try {
      const response = await fetch('/api/pdw/health', { cache: 'no-store' });
      if (!response.ok) throw new Error(`Health endpoint returned ${response.status}`);
      const health: unknown = await response.json();
      if (typeof health === 'object' && health !== null) {
        const status = health as { udpReceiver?: unknown; kafkaPublisherWorker?: unknown };
        this.udpStatus.set(status.udpReceiver === 'UP' ? 'UP' : 'DOWN');
        this.kafkaStatus.set(status.kafkaPublisherWorker === 'UP' ? 'UP' : 'DOWN');
      }
    } catch {
      this.udpStatus.set('UNKNOWN');
      this.kafkaStatus.set('UNKNOWN');
    }
  }
}

interface WaterfallStatusMessage {
  type: 'status';
  receivers: string[];
  defaultReceiverId?: string;
}

function isStatusMessage(value: unknown): value is WaterfallStatusMessage {
  if (typeof value !== 'object' || value === null) return false;
  const message = value as Partial<WaterfallStatusMessage>;
  return message.type === 'status'
    && Array.isArray(message.receivers)
    && message.receivers.every((receiver) => typeof receiver === 'string');
}
