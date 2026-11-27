import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { WaterfallStreamService } from './waterfall-stream.service';
import { AoaStreamService } from './aoa-stream.service';
import { ThemeService } from './theme.service';
import { vi } from 'vitest';
import { routes } from './app.routes';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        { provide: ThemeService, useValue: { theme: signal('light'), toggle: () => undefined } },
        {
          provide: AoaStreamService,
          useValue: {
            status: signal('disconnected'),
            tiles: signal([]),
            receivers: signal(['RX-01']),
            connect: () => undefined,
            disconnect: () => undefined,
            configure: () => undefined,
          },
        },
        {
          provide: WaterfallStreamService,
          useValue: {
            status: signal('disconnected'),
            rows: signal([]),
            receivers: signal(['RX-01']),
            selectedReceiver: signal('RX-01'),
            timeWindowSeconds: signal(10),
            pipelineMetrics: signal({ receivedDatagrams: 0, rejectedDatagrams: 0, decodedPdws: 0, publishedBatches: 0, consumedBatches: 0 }),
            udpStatus: signal('UNKNOWN'),
            kafkaStatus: signal('UNKNOWN'),
            startHealthMonitoring: () => undefined,
            stopHealthMonitoring: () => undefined,
            connect: () => undefined,
            disconnect: () => undefined,
            configure: () => undefined,
          },
        },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders the Pulse Vision navigation shell', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/start');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand strong')?.textContent).toContain('Pulse Vision');
    expect(compiled.querySelectorAll('nav a').length).toBe(3);
    expect(compiled.querySelector('.language-switch')).toBeTruthy();
    expect(compiled.querySelector('.theme-toggle')).toBeTruthy();
  });

  it('shows onboarding with a link to the live display', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/start');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Live-Übersicht öffnen');
  });

  it('renders waterfall and AOA as separate pages', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/dashboard');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Wasserfall');
    expect(compiled.textContent).toContain('Amplitudenschwelle');
    expect(compiled.textContent).not.toContain('Einfallswinkel (AOA)');
    await TestBed.inject(Router).navigateByUrl('/aoa');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Einfallswinkel (AOA)');
  });
});
