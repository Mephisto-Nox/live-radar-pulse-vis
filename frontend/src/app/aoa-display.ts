import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { AoaRadiusMode, AoaSample, AoaSector, AoaTile } from './aoa-types';
import { AoaStreamService } from './aoa-stream.service';
import { LanguageService } from './language.service';

const MAX_POINT_COUNT = 3_000;
const ANGLE_BIN_COUNT = 360;
const RECEIVER_COLORS = ['#70c6f0', '#f5c66b', '#bd9af4', '#81d5ae', '#ef94a8', '#f2a46f'];

@Component({
  selector: 'app-aoa-display',
  styleUrl: './aoa-display.scss',
  templateUrl: './aoa-display.html',
})
export class AoaDisplay implements OnInit, AfterViewInit, OnDestroy {
  protected readonly i18n = inject(LanguageService);
  protected readonly stream = inject(AoaStreamService);
  protected readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('aoaCanvas');
  readonly receiverId = input('RX-01');
  readonly timeWindowSeconds = input(10);
  readonly frequencyFromGHz = input(0);
  readonly frequencyToGHz = input(60);
  readonly amplitudeThresholdDbm = input(-65);
  readonly isPaused = input(false);
  protected readonly sectorChanged = output<AoaSector | null>();
  protected readonly receiverChanged = output<string>();
  protected readonly selectedReceiverIds = signal<string[]>([]);
  protected readonly radiusMode = signal<AoaRadiusMode>('amplitude');
  protected readonly minAmplitudeDbm = signal(-80);
  protected readonly maxAmplitudeDbm = signal(-30);
  protected readonly selectedSector = signal<AoaSector | null>(null);
  protected readonly dragEndAngle = signal<number | null>(null);
  protected readonly cursorReadout = signal('');
  protected readonly clockNow = signal(Date.now());
  protected readonly pausedAtMs = signal<number | null>(null);
  protected readonly displayNowMs = computed(() => this.pausedAtMs() ?? this.clockNow());
  protected readonly statusText = computed(() => {
    if (this.stream.status() === 'connected') return this.i18n.t('connected');
    if (this.stream.status() === 'connecting') return this.i18n.t('connecting');
    if (this.stream.status() === 'error') return this.i18n.t('streamError');
    return this.i18n.t('disconnected');
  });
  protected readonly pausedTiles = signal<AoaTile[] | null>(null);
  protected readonly displayTiles = computed(() => this.pausedTiles() ?? this.stream.tiles());
  protected readonly visibleTiles = computed(() => {
    const receiverIds = this.activeReceiverIds();
    const from = this.displayNowMs() - this.timeWindowSeconds() * 1_000;
    return this.displayTiles().filter((tile) => receiverIds.includes(tile.receiverId)
      && tile.startTimeMs + tile.durationMs >= from);
  });
  protected readonly activeReceiverIds = computed(() => {
    const selected = this.selectedReceiverIds();
    return selected.length > 0 ? selected : [this.receiverId()];
  });
  protected readonly latestTimeMs = computed(() => Math.max(0, ...this.visibleTiles().map((tile) => tile.startTimeMs)));
  protected readonly latencyMs = computed(() => {
    const displayNow = this.displayNowMs();
    const latest = this.latestTimeMs();
    return latest > 0 ? Math.max(0, displayNow - latest) : null;
  });
  protected readonly isStale = computed(() => this.latencyMs() !== null && this.latencyMs()! > 2_000);
  protected readonly hasGap = computed(() => this.visibleTiles().some((tile) => tile.gap));

  private clockTimer?: number;
  private pointerStart: number | null = null;
  private lastDensity?: Uint32Array;
  private lastRadialBinCount = 1;
  private readonly densityCaches = new Map<string, {
    density: Uint32Array;
    tiles: Map<string, AoaTile>;
    mode: AoaRadiusMode;
    radialBinCount: number;
  }>();
  private readonly densityPixelMaps = new Map<number, Int32Array>();
  private densityCanvas?: HTMLCanvasElement;
  private densityImage?: ImageData;
  private frameId?: number;
  private readonly receiverSyncEffect = effect(() => {
    const receiverId = this.receiverId();
    const selected = this.selectedReceiverIds();
    if (selected.length <= 1 && (selected.length !== 1 || selected[0] !== receiverId)) {
      this.selectedReceiverIds.set([receiverId]);
    }
  });
  private readonly pauseEffect = effect(() => {
    if (this.isPaused() && this.pausedTiles() === null) {
      this.pausedTiles.set([...this.stream.tiles()]);
      this.pausedAtMs.set(Date.now());
    } else if (!this.isPaused()) {
      this.pausedTiles.set(null);
      this.pausedAtMs.set(null);
    }
  });
  private readonly configurationEffect = effect(() => {
    const receiverIds = this.activeReceiverIds();
    const minAmplitudeDbm = this.minAmplitudeDbm();
    const maxAmplitudeDbm = this.maxAmplitudeDbm();
    this.stream.configure({
      receiverIds,
      timeWindowSeconds: this.timeWindowSeconds(),
      frequencyFromGHz: this.frequencyFromGHz(),
      frequencyToGHz: this.frequencyToGHz(),
      amplitudeThresholdDbm: this.amplitudeThresholdDbm(),
      minAmplitudeDbm: Math.min(minAmplitudeDbm, maxAmplitudeDbm - 1),
      maxAmplitudeDbm: Math.max(maxAmplitudeDbm, minAmplitudeDbm + 1),
      radiusMode: this.radiusMode(),
    });
  });
  private readonly drawEffect = effect(() => {
    this.visibleTiles();
    this.displayNowMs();
    this.radiusMode();
    this.selectedSector();
    this.dragEndAngle();
    this.minAmplitudeDbm();
    this.maxAmplitudeDbm();
    this.scheduleDraw();
  });

  ngOnInit(): void {
    this.stream.connect();
    this.clockTimer = window.setInterval(() => this.clockNow.set(Date.now()), 500);
  }

  ngAfterViewInit(): void {
    this.scheduleDraw();
  }

  ngOnDestroy(): void {
    window.clearInterval(this.clockTimer);
    if (this.frameId !== undefined) cancelAnimationFrame(this.frameId);
    this.stream.disconnect();
  }

  private scheduleDraw(): void {
    if (this.frameId !== undefined) return;
    this.frameId = requestAnimationFrame(() => {
      this.frameId = undefined;
      this.drawPolarDisplay();
    });
  }

  protected setRadiusMode(value: string): void {
    if (value === 'frequency' || value === 'age' || value === 'amplitude') this.radiusMode.set(value);
  }

  protected setMinAmplitude(value: string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) this.minAmplitudeDbm.set(Math.min(parsed, this.maxAmplitudeDbm() - 1));
  }

  protected setMaxAmplitude(value: string): void {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) this.maxAmplitudeDbm.set(Math.max(parsed, this.minAmplitudeDbm() + 1));
  }

  protected setSelectedReceivers(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const receiverIds = Array.from(select.selectedOptions, (option) => option.value);
    const next = receiverIds.length > 0 ? receiverIds : [this.receiverId()];
    this.selectedReceiverIds.set(next);
    if (next.length === 1 && next[0] !== this.receiverId()) this.receiverChanged.emit(next[0]);
  }

  protected clearSector(): void {
    this.selectedSector.set(null);
    this.sectorChanged.emit(null);
  }

  protected onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    this.pointerStart = this.pointerAngle(event);
    this.dragEndAngle.set(this.pointerStart);
    (event.currentTarget as HTMLCanvasElement).setPointerCapture(event.pointerId);
  }

  protected onPointerUp(event: PointerEvent): void {
    if (this.pointerStart === null) return;
    const end = this.pointerAngle(event);
    const start = this.pointerStart;
    this.pointerStart = null;
    this.dragEndAngle.set(null);
    const angularWidth = (end - start + 360) % 360;
    if (angularWidth < 3 || angularWidth > 357) return;
    const sector = { startDegrees: start, endDegrees: end };
    this.selectedSector.set(sector);
    this.sectorChanged.emit(sector);
  }

  protected onPointerMove(event: PointerEvent): void {
    if (this.pointerStart !== null) {
      this.dragEndAngle.set(this.pointerAngle(event));
      return;
    }
    const canvas = this.canvas()?.nativeElement;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width;
    const y = (event.clientY - bounds.top) / bounds.height;
    const dx = x - 0.5;
    const dy = 0.5 - y;
    const angle = normalizeDegrees(Math.atan2(dx, dy) * 180 / Math.PI);
    const radius = Math.min(1, Math.hypot(dx, dy) * 2);
    const tile = this.visibleTiles().at(-1);
    const radialValue = tile ? this.radialValueAt(radius, tile) : 0;
    const pulseCount = this.countNear(angle, radius);
    const valueText = this.radiusMode() === 'amplitude'
      ? `${(radialValue / 10).toFixed(1)} dBm`
      : this.radiusMode() === 'frequency'
        ? `${(radialValue / 1_000_000).toFixed(3)} GHz`
        : `${(radialValue / 1_000).toFixed(1)} s`;
    this.cursorReadout.set(`${angle.toFixed(1)}° · ${valueText} · ${pulseCount} ${this.i18n.t('pulseDensity')}`);
  }

  protected onPointerLeave(): void {
    this.cursorReadout.set('');
  }

  private drawPolarDisplay(): void {
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
    context.fillStyle = '#091522';
    context.fillRect(0, 0, width, height);
    const centerX = width / 2;
    const centerY = height / 2;
    const radius = Math.min(width, height) * 0.39;
    const tiles = this.visibleTiles();
    const mode = this.radiusMode();
    const radialBinCount = mode === 'age' ? 50 : tiles.at(-1)?.radialBinCount ?? 50;
    const density = this.buildDensity(tiles, radialBinCount, mode);
    this.lastDensity = density;
    this.lastRadialBinCount = radialBinCount;
    const totalPulseCount = tiles.reduce((sum, tile) => sum + tile.pulseCount, 0);
    if (totalPulseCount > MAX_POINT_COUNT || tiles.some((tile) => tile.samples.length < tile.pulseCount)) {
      const receiverIds = this.activeReceiverIds();
      if (receiverIds.length > 1) {
        receiverIds.forEach((receiverId, index) => {
          const receiverTiles = tiles.filter((tile) => tile.receiverId === receiverId);
          const receiverDensity = this.buildDensity(receiverTiles, radialBinCount, mode);
          this.drawDensity(context, receiverDensity, radialBinCount, centerX, centerY, radius, pixelRatio, index);
        });
      } else {
        this.drawDensity(context, density, radialBinCount, centerX, centerY, radius, pixelRatio);
      }
    } else {
      this.drawSamples(context, tiles, centerX, centerY, radius, pixelRatio);
    }
    this.drawGrid(context, centerX, centerY, radius, radialBinCount, mode, pixelRatio, tiles.at(-1));
    this.drawSector(context, centerX, centerY, radius);
    context.fillStyle = '#c8d8e5';
    context.beginPath();
    context.arc(centerX, centerY, Math.max(2, 2.5 * pixelRatio), 0, Math.PI * 2);
    context.fill();
  }

  private buildDensity(tiles: AoaTile[], radialBinCount: number, mode: AoaRadiusMode): Uint32Array {
    if (mode === 'age') return this.buildAgeDensity(tiles, radialBinCount);
    const receiverKey = [...new Set(tiles.map((tile) => tile.receiverId))].sort().join('\0');
    const cacheKey = `${mode}:${radialBinCount}:${receiverKey}`;
    let cache = this.densityCaches.get(cacheKey);
    if (!cache) {
      cache = {
        density: new Uint32Array(ANGLE_BIN_COUNT * radialBinCount),
        tiles: new Map<string, AoaTile>(),
        mode,
        radialBinCount,
      };
      this.densityCaches.set(cacheKey, cache);
      if (this.densityCaches.size > 8) {
        const oldestKey = this.densityCaches.keys().next().value;
        if (oldestKey !== undefined && oldestKey !== cacheKey) this.densityCaches.delete(oldestKey);
      }
    }
    const desiredTiles = new Map(tiles.map((tile) => [this.tileKey(tile), tile]));
    for (const [key, cachedTile] of cache.tiles) {
      if (desiredTiles.get(key) !== cachedTile) {
        this.addTileDensity(cache.density, cachedTile, cache.radialBinCount, -1);
        cache.tiles.delete(key);
      }
    }
    for (const [key, tile] of desiredTiles) {
      if (!cache.tiles.has(key)) {
        this.addTileDensity(cache.density, tile, cache.radialBinCount, 1);
        cache.tiles.set(key, tile);
      }
    }
    return cache.density;
  }

  private buildAgeDensity(tiles: AoaTile[], radialBinCount: number): Uint32Array {
    const density = new Uint32Array(ANGLE_BIN_COUNT * radialBinCount);
    const now = this.displayNowMs();
    const maxAge = this.timeWindowSeconds() * 1_000;
    for (const tile of tiles) {
      const age = Math.max(0, now - tile.startTimeMs);
      const ageBin = Math.min(radialBinCount - 1, Math.floor(age * radialBinCount / Math.max(1, maxAge)));
      const targetOffset = ageBin * ANGLE_BIN_COUNT;
      for (let radialBin = 0; radialBin < tile.radialBinCount; radialBin++) {
        const sourceOffset = radialBin * tile.angleBinCount;
        for (let angleBin = 0; angleBin < tile.angleBinCount; angleBin++) {
          density[targetOffset + angleBin] += tile.counts[sourceOffset + angleBin] ?? 0;
        }
      }
    }
    return density;
  }

  private addTileDensity(
    density: Uint32Array,
    tile: AoaTile,
    radialBinCount: number,
    direction: 1 | -1,
  ): void {
    const bins = Math.min(radialBinCount, tile.radialBinCount);
    for (let radialBin = 0; radialBin < bins; radialBin++) {
      const sourceOffset = radialBin * tile.angleBinCount;
      for (let angleBin = 0; angleBin < tile.angleBinCount; angleBin++) {
        const index = radialBin * ANGLE_BIN_COUNT + angleBin;
        density[index] = Math.max(0, density[index] + direction * (tile.counts[sourceOffset + angleBin] ?? 0));
      }
    }
  }

  private tileKey(tile: AoaTile): string {
    return `${tile.receiverId}\0${tile.startTimeMs}`;
  }

  private drawGrid(
    context: CanvasRenderingContext2D,
    centerX: number,
    centerY: number,
    radius: number,
    radialBinCount: number,
    mode: AoaRadiusMode,
    pixelRatio: number,
    tile?: AoaTile,
  ): void {
    context.lineWidth = Math.max(1, pixelRatio);
    context.strokeStyle = 'rgba(177, 201, 220, .25)';
    for (let ring = 1; ring <= radialBinCount; ring++) {
      if (mode === 'amplitude' && ring % 10 !== 0) continue;
      if (mode !== 'amplitude' && ring % 10 !== 0 && ring !== radialBinCount) continue;
      context.beginPath();
      context.arc(centerX, centerY, radius * ring / radialBinCount, 0, Math.PI * 2);
      context.stroke();
    }
    context.strokeStyle = 'rgba(177, 201, 220, .16)';
    for (let angle = 0; angle < 360; angle += 30) {
      const radians = angle * Math.PI / 180 - Math.PI / 2;
      context.beginPath();
      context.moveTo(centerX, centerY);
      context.lineTo(centerX + Math.cos(radians) * radius, centerY + Math.sin(radians) * radius);
      context.stroke();
    }
    context.fillStyle = '#9db2c4';
    context.font = `${Math.max(11, 11 * pixelRatio)}px Segoe UI, sans-serif`;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(`0° · ${this.i18n.t('north')}`, centerX, centerY - radius * .88);
    context.fillText(`90° · ${this.i18n.t('east')}`, centerX + radius * .82, centerY);
    context.fillText(`180° · ${this.i18n.t('south')}`, centerX, centerY + radius * .88);
    context.fillText(`270° · ${this.i18n.t('west')}`, centerX - radius * .82, centerY);
    for (let ring = 10; ring <= radialBinCount; ring += 10) {
      const value = this.ringValue(ring / radialBinCount, mode, tile);
      context.textAlign = 'left';
      context.textBaseline = 'bottom';
      context.fillText(value, centerX + 4 * pixelRatio, centerY - radius * ring / radialBinCount - 2 * pixelRatio);
    }
  }

  private drawDensity(
    context: CanvasRenderingContext2D,
    density: Uint32Array,
    radialBinCount: number,
    centerX: number,
    centerY: number,
    radius: number,
    pixelRatio: number,
    receiverIndex?: number,
  ): void {
    let maximum = 0;
    for (const count of density) maximum = Math.max(maximum, count);
    if (maximum === 0) return;
    const size = ANGLE_BIN_COUNT;
    this.densityCanvas ??= document.createElement('canvas');
    if (this.densityCanvas.width !== size || this.densityCanvas.height !== size) {
      this.densityCanvas.width = size;
      this.densityCanvas.height = size;
    }
    const offscreen = this.densityCanvas.getContext('2d');
    if (!offscreen) return;
    this.densityImage ??= offscreen.createImageData(size, size);
    const image = this.densityImage;
    const pixels = image.data;
    pixels.fill(0);
    const pixelMap = this.densityPixelMap(radialBinCount);
    const palette = densityPalette(receiverIndex === undefined
      ? undefined
      : RECEIVER_COLORS[receiverIndex % RECEIVER_COLORS.length]);
    for (let pixel = 0; pixel < pixelMap.length; pixel++) {
      const densityIndex = pixelMap[pixel];
      if (densityIndex < 0) continue;
      const count = density[densityIndex];
      if (count === 0) continue;
      const level = Math.min(255, Math.round(Math.log1p(count) / Math.log1p(maximum) * 255));
      const paletteOffset = level * 3;
      const pixelOffset = pixel * 4;
      pixels[pixelOffset] = palette[paletteOffset];
      pixels[pixelOffset + 1] = palette[paletteOffset + 1];
      pixels[pixelOffset + 2] = palette[paletteOffset + 2];
      pixels[pixelOffset + 3] = 235;
    }
    offscreen.putImageData(image, 0, 0);
    context.drawImage(this.densityCanvas, centerX - radius, centerY - radius, radius * 2, radius * 2);
  }

  private densityPixelMap(radialBinCount: number): Int32Array {
    const cached = this.densityPixelMaps.get(radialBinCount);
    if (cached) return cached;
    const size = ANGLE_BIN_COUNT;
    const center = (size - 1) / 2;
    const pixelMap = new Int32Array(size * size).fill(-1);
    for (let y = 0; y < size; y++) {
      const dy = center - y;
      for (let x = 0; x < size; x++) {
        const dx = x - center;
        const radiusRatio = Math.hypot(dx, dy) / center;
        if (radiusRatio > 1) continue;
        const angle = normalizeDegrees(Math.atan2(dx, dy) * 180 / Math.PI);
        const angleBin = Math.floor(angle) % ANGLE_BIN_COUNT;
        const radialBin = Math.min(radialBinCount - 1, Math.floor(radiusRatio * radialBinCount));
        pixelMap[y * size + x] = radialBin * ANGLE_BIN_COUNT + angleBin;
      }
    }
    this.densityPixelMaps.set(radialBinCount, pixelMap);
    if (this.densityPixelMaps.size > 4) {
      const oldestCount = this.densityPixelMaps.keys().next().value;
      if (oldestCount !== undefined && oldestCount !== radialBinCount) this.densityPixelMaps.delete(oldestCount);
    }
    return pixelMap;
  }

  private drawSamples(
    context: CanvasRenderingContext2D,
    tiles: AoaTile[],
    centerX: number,
    centerY: number,
    radius: number,
    pixelRatio: number,
  ): void {
    const now = this.displayNowMs();
    const windowMs = this.timeWindowSeconds() * 1_000;
    const multiReceiver = this.activeReceiverIds().length > 1;
    const dotSize = Math.max(2, 2.2 * pixelRatio);
    for (const tile of tiles) {
      const age = Math.max(0, now - tile.startTimeMs);
      const alpha = Math.max(.16, 1 - age / windowMs);
      const color = multiReceiver
        ? RECEIVER_COLORS[Math.max(0, this.activeReceiverIds().indexOf(tile.receiverId)) % RECEIVER_COLORS.length]
        : '#75c8f2';
      context.fillStyle = color;
      context.globalAlpha = alpha;
      for (const sample of tile.samples) {
        const radialRatio = this.sampleRadius(sample, tile, age);
        const angle = sample.angleHundredthDegree / 100 * Math.PI / 180 - Math.PI / 2;
        const pointRadius = Math.max(2, radius * radialRatio);
        context.beginPath();
        context.arc(centerX + Math.cos(angle) * pointRadius, centerY + Math.sin(angle) * pointRadius,
          dotSize / 2, 0, Math.PI * 2);
        context.fill();
      }
    }
    context.globalAlpha = 1;
  }

  private drawSector(context: CanvasRenderingContext2D, centerX: number, centerY: number, radius: number): void {
    const sector = this.pointerStart !== null && this.dragEndAngle() !== null
      ? { startDegrees: this.pointerStart, endDegrees: this.dragEndAngle()! }
      : this.selectedSector();
    if (!sector) return;
    const start = sector.startDegrees * Math.PI / 180 - Math.PI / 2;
    const end = sector.endDegrees * Math.PI / 180 - Math.PI / 2;
    context.beginPath();
    context.moveTo(centerX, centerY);
    context.arc(centerX, centerY, radius, start, end);
    context.closePath();
    context.fillStyle = 'rgba(245, 202, 107, .1)';
    context.fill();
    context.strokeStyle = 'rgba(245, 202, 107, .8)';
    context.lineWidth = 1.5;
    context.stroke();
  }

  private sampleRadius(sample: AoaSample, tile: AoaTile, ageMs: number): number {
    if (this.radiusMode() === 'amplitude') {
      return clamp((sample.amplitudeTenthDbm - tile.radialMinValue)
        / Math.max(1, tile.radialMaxValue - tile.radialMinValue), 0, 1);
    }
    if (this.radiusMode() === 'frequency') {
      return clamp((sample.frequencyKHz - tile.minFrequencyKHz)
        / Math.max(1, tile.maxFrequencyKHz - tile.minFrequencyKHz), 0, 1);
    }
    return clamp(ageMs / (this.timeWindowSeconds() * 1_000), 0, 1);
  }

  private ringValue(ratio: number, mode: AoaRadiusMode, tile?: AoaTile): string {
    if (mode === 'amplitude') {
      const min = tile?.radialMinValue ?? this.minAmplitudeDbm() * 10;
      const max = tile?.radialMaxValue ?? this.maxAmplitudeDbm() * 10;
      return `${((min + (max - min) * ratio) / 10).toFixed(0)} dBm`;
    }
    if (mode === 'frequency') {
      const min = tile?.radialMinValue ?? this.frequencyFromGHz() * 1_000_000;
      const max = tile?.radialMaxValue ?? this.frequencyToGHz() * 1_000_000;
      return `${((min + (max - min) * ratio) / 1_000_000).toFixed(1)} GHz`;
    }
    return `${(this.timeWindowSeconds() * ratio).toFixed(0)}s`;
  }

  private radialValueAt(ratio: number, tile: AoaTile): number {
    return tile.radialMinValue + (tile.radialMaxValue - tile.radialMinValue) * ratio;
  }

  private countNear(angleDegrees: number, radiusRatio: number): number {
    const density = this.lastDensity;
    if (!density) return 0;
    const angleBin = Math.floor(normalizeDegrees(angleDegrees));
    const radialBin = Math.min(this.lastRadialBinCount - 1,
      Math.max(0, Math.floor(radiusRatio * this.lastRadialBinCount)));
    let count = 0;
    for (let radial = Math.max(0, radialBin - 1); radial <= Math.min(this.lastRadialBinCount - 1, radialBin + 1); radial++) {
      for (let delta = -1; delta <= 1; delta++) {
        const angle = (angleBin + delta + ANGLE_BIN_COUNT) % ANGLE_BIN_COUNT;
        count += density[radial * 360 + angle];
      }
    }
    return count;
  }

  private pointerAngle(event: PointerEvent): number {
    const canvas = event.currentTarget as HTMLCanvasElement;
    const bounds = canvas.getBoundingClientRect();
    const dx = event.clientX - (bounds.left + bounds.width / 2);
    const dy = (bounds.top + bounds.height / 2) - event.clientY;
    return normalizeDegrees(Math.atan2(dx, dy) * 180 / Math.PI);
  }

  protected receiverColor(receiverId: string): string {
    const index = Math.max(0, this.activeReceiverIds().indexOf(receiverId));
    return RECEIVER_COLORS[index % RECEIVER_COLORS.length];
  }
}

function normalizeDegrees(value: number): number {
  return (value % 360 + 360) % 360;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max));
}

function densityPalette(receiverColor?: string): Uint8Array {
  const palette = new Uint8Array(256 * 3);
  const target = receiverColor
    ? [0, 2, 4].map((offset) => Number.parseInt(receiverColor.slice(offset + 1, offset + 3), 16))
    : undefined;
  for (let level = 0; level < 256; level++) {
    const intensity = level / 255;
    let channels: number[];
    if (target) {
      const strength = .18 + intensity * .78;
      channels = target.map((value, index) => Math.round([9, 21, 34][index] + (value - [9, 21, 34][index]) * strength));
    } else if (intensity < .35) {
      channels = blendChannels([16, 54, 82], [39, 130, 181], intensity / .35);
    } else if (intensity < .7) {
      channels = blendChannels([39, 130, 181], [62, 194, 206], (intensity - .35) / .35);
    } else {
      channels = blendChannels([62, 194, 206], [250, 197, 102], (intensity - .7) / .3);
    }
    palette.set(channels, level * 3);
  }
  return palette;
}

function blendChannels(from: number[], to: number[], ratio: number): number[] {
  return from.map((value, index) => Math.round(value + (to[index] - value) * clamp(ratio, 0, 1)));
}


