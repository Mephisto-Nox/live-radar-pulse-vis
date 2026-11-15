import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { PacketStreamService } from './packet-stream.service';

@Component({
  selector: 'app-live-dashboard-page',
  imports: [DatePipe],
  styleUrl: './live-dashboard-page.scss',
  templateUrl: './live-dashboard-page.html',
})
export class LiveDashboardPage implements OnInit, OnDestroy {
  protected readonly stream = inject(PacketStreamService);

  ngOnInit(): void {
    this.stream.connect();
  }

  ngOnDestroy(): void {
    this.stream.disconnect();
  }
}
