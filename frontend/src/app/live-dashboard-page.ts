import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { AoaDisplay } from './aoa-display';
import { LanguageService } from './language.service';
import { WaterfallRow, WaterfallStreamService } from './waterfall-stream.service';

const AMPLITUDE_BIN_WIDTH_TENTH_DBM = 10;
const ROWS_PER_SECOND = 10;

@Component({
  selector: 'app-live-dashboard-page',
  imports: [DecimalPipe, AoaDisplay],
  styleUrl: './live-dashboard-page.scss',
  templateUrl: './live-dashboard-page.html',
})
export class LiveDashboardPage implements OnInit, OnDestroy {
  protected readonly i18n = inject(LanguageService);
  protected readonly stream = inject(WaterfallStreamService);
  protected readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('waterfallCanvas');
  protected readonly frequencyFromGHz = signal(8);
  protected readonly frequencyToGHz = signal(12);
  protected readonly amplitudeThresholdDbm = signal(-65);
  protected readonly isPaused = signal(false);
  protected readonly selectedRange = signal<string | null>(null);
  protected readonly cursorReadout = signal('');
  protected readonly pausedRows = signal<WaterfallRow[] | null>(null);
  protected readonly displayRows = computed(() => this.pausedRows() ?? this.stream.rows());
  protected readonly clockNow = signal(Date.now());
  protected readonly statusText = computed(() => {
    if (this.stream.status() === 'connected') return this.i18n.t('connected');
    if (this.stream.status() === 'connecting') return this.i18n.t('connecting');
    if (this.stream.status() === 'error') return this.i18n.t('streamError');
    return this.i18n.t('disconnected');
  });
  protected readonly latestRow = computed(() => this.displayRows().at(-1));
  protected readonly latencyMs = computed(() => {
    this.clockNow();
    const latest = this.latestRow();
    return latest ? Math.max(0, Date.now() - latest.startTimeMs) : null;
  });
  protected readonly isStale = computed(() => this.latencyMs() !== null && this.latencyMs()! > 2_000);
  protected readonly hasGap = computed(() => {
    const rows = this.displayRows();
    const visibleCount = this.stream.timeWindowSeconds() * ROWS_PER_SECOND;
    return rows.slice(-visibleCount).some((row) => row.gap);
  });
  protected readonly reductionPercent = computed(() => {
    const rows = this.visibleRows();
    const input = rows.reduce((sum, row) => sum + row.inputPulseCount, 0);
    if (input === 0) return 0;
    const accepted = rows.reduce((sum, row) => sum + this.filteredPulseCount(row), 0);
    return Math.max(0, Math.min(100, (1 - accepted / input) * 100));
  });

  protected readonly visibleRows = computed(() => {
    const rowCount = this.stream.timeWindowSeconds() * ROWS_PER_SECOND;
    return this.displayRows().filter((row) => row.receiverId === this.stream.selectedReceiver()).slice(-rowCount);
  });
  private clockTimer?: number;
  private pointerStartX: number | null = null;
  private readonly drawEffect = effect(() => {
    this.displayRows();
    this.frequencyFromGHz();
    this.frequencyToGHz();
    this.amplitudeThresholdDbm();
    this.stream.timeWindowSeconds();
    this.stream.selectedReceiver();
    this.clockNow();
    requestAnimationFrame(() => this.drawWaterfall());
  });

  ngOnInit(): void {
    this.clockTimer = window.setInterval(() => this.clockNow.set(Date.now()), 500);
  }

  ngOnDestroy(): void {
    window.clearInterval(this.clockTimer);
  }

  protected setTimeWindow(seconds: number): void {
    this.stream.configure(this.stream.selectedReceiver(), seconds);
  }

  protected setReceiver(receiverId: string): void {
    this.stream.configure(receiverId, this.stream.timeWindowSeconds());
  }

  protected setFrequencyFrom(value: string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) this.frequencyFromGHz.set(Math.max(8, Math.min(parsed, this.frequencyToGHz() - 0.1)));
  }

  protected setFrequencyTo(value: string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) this.frequencyToGHz.set(Math.min(12, Math.max(parsed, this.frequencyFromGHz() + 0.1)));
  }

  protected setAmplitude(value: string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) this.amplitudeThresholdDbm.set(parsed);
  }

  protected togglePause(): void {
    if (this.isPaused()) {
      this.isPaused.set(false);
      this.pausedRows.set(null);
    } else {
      this.pausedRows.set([...this.stream.rows()]);
      this.isPaused.set(true);
    }
  }

  protected onPointerMove(event: PointerEvent): void {
    const canvas = this.canvas()?.nativeElement;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    const xRatio = Math.max(0, Math.min(0.999_999, (event.clientX - bounds.left) / bounds.width));
    const yRatio = Math.max(0, Math.min(0.999_999, (event.clientY - bounds.top) / bounds.height));
    const frequencyGHz = this.frequencyFromGHz() + xRatio * (this.frequencyToGHz() - this.frequencyFromGHz());
    const reversedRows = [...this.visibleRows()].reverse();
    const row = reversedRows[Math.floor(yRatio * reversedRows.length)];
    const pulseCount = row ? this.filteredPulseCountAtFrequency(row, frequencyGHz) : 0;
    const locale = this.i18n.language() === 'de' ? 'de-DE' : 'en-US';
    const timeLabel = row ? new Date(row.startTimeMs).toLocaleTimeString(locale, { hour12: false }) : '—';
    this.cursorReadout.set(`${frequencyGHz.toFixed(3)} GHz · ${timeLabel} · ${pulseCount} ${this.i18n.t('pulseDensity')}`);
  }

  protected onPointerLeave(): void {
    this.cursorReadout.set('');
  }

  protected onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    const bounds = (event.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    this.pointerStartX = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
    (event.currentTarget as HTMLCanvasElement).setPointerCapture(event.pointerId);
  }

  protected onPointerUp(event: PointerEvent): void {
    if (this.pointerStartX === null) return;
    const bounds = (event.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    const endX = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
    const low = Math.min(this.pointerStartX, endX);
    const high = Math.max(this.pointerStartX, endX);
    this.pointerStartX = null;
    if (high - low < 0.02) return;
    const widthGHz = this.frequencyToGHz() - this.frequencyFromGHz();
    const from = Math.max(8, Math.floor((this.frequencyFromGHz() + low * widthGHz) * 10) / 10);
    const to = Math.min(12, Math.ceil((this.frequencyFromGHz() + high * widthGHz) * 10) / 10);
    this.frequencyFromGHz.set(from);
    this.frequencyToGHz.set(Math.max(from + 0.1, to));
    this.selectedRange.set(`${from.toFixed(1)}–${Math.max(from + 0.1, to).toFixed(1)} GHz`);
  }

  protected useFullFrequencyRange(): void {
    this.frequencyFromGHz.set(8);
    this.frequencyToGHz.set(12);
    this.selectedRange.set(null);
  }

  private drawWaterfall(): void {
    const canvas = this.canvas()?.nativeElement;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    const pixelRatio = window.devicePixelRatio || 1;
    const width = Math.max(1, Math.floor(bounds.width * pixelRatio));
    const height = Math.max(1, Math.floor(bounds.height * pixelRatio));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const context = canvas.getContext('2d');
    if (!context) return;
    context.fillStyle = '#0b1522';
    context.fillRect(0, 0, width, height);

    const visibleCount = Math.max(1, this.stream.timeWindowSeconds() * ROWS_PER_SECOND);
    const displayedRows = this.visibleRows().slice(-visibleCount).reverse();
    const rowHeight = height / visibleCount;
    const startBin = Math.max(0, Math.floor((this.frequencyFromGHz() - 8) * 50));
    const endBin = Math.min(displayedRows[0]?.frequencyBinCount ?? 200, Math.ceil((this.frequencyToGHz() - 8) * 50));
    const binCount = Math.max(1, endBin - startBin);
    const minAmplitudeBin = Math.max(0, Math.ceil((this.amplitudeThresholdDbm() * 10 - (displayedRows[0]?.minPowerTenthDbm ?? -800)) / AMPLITUDE_BIN_WIDTH_TENTH_DBM));
    let maximumCount = 0;
    for (const row of displayedRows) {
      for (let frequencyBin = startBin; frequencyBin < endBin; frequencyBin++) {
        maximumCount = Math.max(maximumCount, this.sumAmplitudeBins(row, frequencyBin, minAmplitudeBin));
      }
    }

    displayedRows.forEach((row, rowIndex) => {
      const y = Math.floor(rowIndex * rowHeight);
      const nextY = Math.ceil((rowIndex + 1) * rowHeight);
      if (row.gap) {
        context.fillStyle = '#152334';
        context.fillRect(0, y, width, Math.max(1, nextY - y));
        context.strokeStyle = 'rgba(144, 164, 184, .24)';
        context.lineWidth = Math.max(1, pixelRatio);
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(width, nextY);
        context.stroke();
        return;
      }
      for (let localBin = 0; localBin < binCount; localBin++) {
        const count = this.sumAmplitudeBins(row, startBin + localBin, minAmplitudeBin);
        if (count === 0) continue;
        const intensity = Math.log1p(count) / Math.log1p(Math.max(1, maximumCount));
        context.fillStyle = densityColor(intensity);
        const x = Math.floor(localBin * width / binCount);
        const nextX = Math.ceil((localBin + 1) * width / binCount);
        context.fillRect(x, y, Math.max(1, nextX - x), Math.max(1, nextY - y));
      }
    });

    context.strokeStyle = 'rgba(197, 216, 233, .15)';
    context.lineWidth = Math.max(1, pixelRatio);
    for (let tick = 1; tick < 4; tick++) {
      const y = Math.round(tick * height / 4);
      context.beginPath();
      context.moveTo(0, y);
      context.lineTo(width, y);
      context.stroke();
    }
    context.strokeStyle = 'rgba(197, 216, 233, .22)';
    for (let tick = 1; tick < 5; tick++) {
      const x = Math.round(tick * width / 5);
      context.beginPath();
      context.moveTo(x, 0);
      context.lineTo(x, height);
      context.stroke();
    }
  }

  private sumAmplitudeBins(row: WaterfallRow, frequencyBin: number, startAmplitudeBin: number): number {
    let count = 0;
    for (let amplitudeBin = startAmplitudeBin; amplitudeBin < row.amplitudeBinCount; amplitudeBin++) {
      count += row.counts[amplitudeBin * row.frequencyBinCount + frequencyBin] ?? 0;
    }
    return count;
  }

  private filteredPulseCount(row: WaterfallRow): number {
    const from = Math.max(0, Math.floor((this.frequencyFromGHz() - 8) * row.frequencyBinCount / 4));
    const to = Math.min(row.frequencyBinCount, Math.ceil((this.frequencyToGHz() - 8) * row.frequencyBinCount / 4));
    const minAmplitudeBin = Math.max(0, Math.ceil((this.amplitudeThresholdDbm() * 10 - row.minPowerTenthDbm) / AMPLITUDE_BIN_WIDTH_TENTH_DBM));
    let count = 0;
    for (let amplitudeBin = minAmplitudeBin; amplitudeBin < row.amplitudeBinCount; amplitudeBin++) {
      const offset = amplitudeBin * row.frequencyBinCount;
      for (let frequencyBin = from; frequencyBin < to; frequencyBin++) count += row.counts[offset + frequencyBin] ?? 0;
    }
    return count;
  }

  private filteredPulseCountAtFrequency(row: WaterfallRow, frequencyGHz: number): number {
    const frequencyBin = Math.max(0, Math.min(row.frequencyBinCount - 1,
      Math.floor((frequencyGHz - 8) * row.frequencyBinCount / 4)));
    return this.sumAmplitudeBins(row, frequencyBin,
      Math.max(0, Math.ceil((this.amplitudeThresholdDbm() * 10 - row.minPowerTenthDbm) / AMPLITUDE_BIN_WIDTH_TENTH_DBM)));
  }
}

function densityColor(intensity: number): string {
  if (intensity < 0.25) return blendColor([11, 24, 39], [21, 91, 125], intensity / 0.25);
  if (intensity < 0.6) return blendColor([21, 91, 125], [39, 183, 190], (intensity - 0.25) / 0.35);
  return blendColor([39, 183, 190], [248, 194, 89], (intensity - 0.6) / 0.4);
}

function blendColor(from: number[], to: number[], ratio: number): string {
  const channels = from.map((value, index) => Math.round(value + (to[index] - value) * Math.max(0, Math.min(1, ratio))));
  return `rgb(${channels[0]} ${channels[1]} ${channels[2]})`;
}
