import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PacketStreamService } from './packet-stream.service';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly packetStream = inject(PacketStreamService);
  protected readonly stream = this.packetStream;
  protected readonly connectionLabel = computed(() => {
    switch (this.stream.status()) {
      case 'connected': return 'Paketstream verbunden';
      case 'connecting': return 'Paketstream verbindet';
      case 'error': return 'Paketstream gestört';
      default: return 'Paketstream getrennt';
    }
  });
}
