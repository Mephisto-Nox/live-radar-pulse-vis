import { Injectable, signal } from '@angular/core';

export interface ReceivedPacket {
  receiverId: string;
  sequenceNumber: number;
  receivedAt: string;
  payloadLengthBytes: number;
}

export type StreamStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

const MAX_VISIBLE_PACKETS = 100;

@Injectable({ providedIn: 'root' })
export class PacketStreamService {
  readonly status = signal<StreamStatus>('disconnected');
  readonly packets = signal<ReceivedPacket[]>([]);

  private socket?: WebSocket;

  connect(): void {
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) {
      return;
    }

    this.status.set('connecting');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/packets`);
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket === socket) {
        this.status.set('connected');
      }
    };
    socket.onmessage = (event: MessageEvent<string>) => this.handleMessage(socket, event.data);
    socket.onerror = () => {
      if (this.socket === socket) {
        this.status.set('error');
      }
    };
    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = undefined;
        this.status.set('disconnected');
      }
    };
  }

  disconnect(): void {
    const socket = this.socket;
    this.socket = undefined;
    socket?.close();
    this.status.set('disconnected');
  }

  private handleMessage(socket: WebSocket, message: string): void {
    if (this.socket !== socket) {
      return;
    }

    try {
      const packet: unknown = JSON.parse(message);
      if (isReceivedPacket(packet)) {
        this.packets.update((packets) => [packet, ...packets].slice(0, MAX_VISIBLE_PACKETS));
      }
    } catch {
      this.status.set('error');
    }
  }
}

function isReceivedPacket(value: unknown): value is ReceivedPacket {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const packet = value as Partial<ReceivedPacket>;
  return typeof packet.receiverId === 'string'
    && packet.receiverId.length > 0
    && typeof packet.sequenceNumber === 'number'
    && Number.isSafeInteger(packet.sequenceNumber)
    && typeof packet.receivedAt === 'string'
    && Number.isFinite(Date.parse(packet.receivedAt))
    && typeof packet.payloadLengthBytes === 'number'
    && Number.isSafeInteger(packet.payloadLengthBytes)
    && packet.payloadLengthBytes >= 0;
}
