import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { AoaDisplay } from './aoa-display';
import { AoaStreamService } from './aoa-stream.service';
import { AoaSector } from './aoa-types';
import { LanguageService } from './language.service';
import { WaterfallStreamService } from './waterfall-stream.service';

@Component({
  selector: 'app-aoa-page',
  imports: [AoaDisplay],
  styleUrl: './aoa-page.scss',
  templateUrl: './aoa-page.html',
})
export class AoaPage implements OnInit, OnDestroy {
  protected readonly i18n = inject(LanguageService);
  protected readonly stream = inject(AoaStreamService);
  protected readonly pipeline = inject(WaterfallStreamService);
  protected readonly receiverId = signal('RX-01');
  protected readonly timeWindowSeconds = signal(10);
  protected readonly frequencyFromGHz = signal(0);
  protected readonly frequencyToGHz = signal(60);
  protected readonly amplitudeThresholdDbm = signal(-65);
  protected readonly isPaused = signal(false);
  protected readonly statusText = computed(() => {
    switch (this.stream.status()) {
      case 'connected': return this.i18n.t('connected');
      case 'connecting': return this.i18n.t('connecting');
      case 'error': return this.i18n.t('streamError');
      default: return this.i18n.t('disconnected');
    }
  });
  protected readonly dataHint = computed(() => {
    if (this.stream.status() !== 'connected') return this.i18n.t('aoaWebsocketUnavailable');
    if (this.pipeline.pipelineMetrics().receivedDatagrams === 0) return this.i18n.t('noDatagramsHint');
    if (this.stream.tiles().length === 0) return this.i18n.t('aoaNoTilesHint');
    return this.i18n.t('aoaTilesArrived');
  });

  ngOnInit(): void {
    this.stream.connect();
  }

  ngOnDestroy(): void {
    this.stream.disconnect();
  }

  protected setTimeWindow(value: string): void {
    const seconds = Number(value);
    if (seconds === 5 || seconds === 10 || seconds === 30) this.timeWindowSeconds.set(seconds);
  }

  protected setFrequencyFrom(value: string): void {
    const from = Number(value);
    if (Number.isFinite(from)) this.frequencyFromGHz.set(Math.max(0, Math.min(from, this.frequencyToGHz() - .1)));
  }

  protected setFrequencyTo(value: string): void {
    const to = Number(value);
    if (Number.isFinite(to)) this.frequencyToGHz.set(Math.min(60, Math.max(to, this.frequencyFromGHz() + .1)));
  }

  protected setAmplitude(value: string): void {
    const amplitude = Number(value);
    if (Number.isFinite(amplitude)) this.amplitudeThresholdDbm.set(amplitude);
  }

  protected setReceiver(receiverId: string): void {
    this.receiverId.set(receiverId);
  }

  protected setSector(sector: AoaSector | null): void {
    this.pipeline.configureAoaSector(sector);
  }
}
