import { Injectable, signal } from '@angular/core';

export type Language = 'de' | 'en';

const translations = {
  de: {
    start: 'Start',
    liveOverview: 'Live-Übersicht',
    workspace: 'Arbeitsbereich',
    localEnvironment: 'Lokale Umgebung',
    welcome: 'Pulse Vision',
    welcomeIntro: 'Live-Lagebild der empfangenen Radarsignale',
    openDashboard: 'Live-Übersicht öffnen',
    pipelineStatus: 'Systemstatus',
    udpReceiver: 'UDP-Empfang',
    kafkaFlow: 'Kafka-Datenstrom',
    noDataYet: 'Warte auf erste PDW-Daten',
    dashboardTitle: 'Wasserfall',
    dashboardSubtitle: 'Frequenzbelegung über Zeit · neueste Zeile oben',
    connected: 'Datenstrom aktiv',
    connecting: 'Verbinde …',
    disconnected: 'Datenstrom getrennt',
    streamError: 'Datenstrom gestört',
    receiver: 'Empfänger',
    timeWindow: 'Zeitfenster',
    frequencyBand: 'Frequenzband',
    amplitudeThreshold: 'Amplitudenschwelle',
    pause: 'Pause',
    resume: 'Fortsetzen',
    paused: 'Pausiert',
    now: 'JETZT',
    frequency: 'Frequenz',
    pulseDensity: 'Pulse / 100 ms',
    reduction: 'Reduktion',
    latency: 'Latenz',
    gapWarning: 'Datenlücke erkannt — fehlende Zeilen werden nicht interpoliert.',
    staleWarning: 'Datenstrom verzögert. Die Anzeige zeigt den letzten empfangenen Stand.',
    noWaterfallData: 'Noch keine Wasserfall-Kacheln',
    noWaterfallHint: 'Prüfe den UDP-Empfang und den Kafka-Datenstrom. Sobald PDW-Daten eintreffen, erscheint hier die Frequenzbelegung.',
    densityLegend: 'Pulsdichte · logarithmische Farbskala',
    aoaTitle: 'Einfallswinkel (AOA)',
    aoaPending: 'Datenintegration folgt',
    amplitude: 'Amplitude',
    language: 'Sprache',
    north: 'N',
    south: 'S',
    east: 'O',
    west: 'W',
    mainScreen: 'Hauptanzeige',
    receiverUnavailable: 'Noch kein Empfänger erkannt',
    frequencyStart: 'Von (GHz)',
    frequencyEnd: 'Bis (GHz)',
    thresholdDbm: 'dBm',
    selectedBand: 'Markierten Bereich übernehmen',
  },
  en: {
    start: 'Start',
    liveOverview: 'Live overview',
    workspace: 'Workspace',
    localEnvironment: 'Local environment',
    welcome: 'Pulse Vision',
    welcomeIntro: 'Live picture of received radar signals',
    openDashboard: 'Open live overview',
    pipelineStatus: 'System status',
    udpReceiver: 'UDP receiver',
    kafkaFlow: 'Kafka stream',
    noDataYet: 'Waiting for first PDW data',
    dashboardTitle: 'Waterfall',
    dashboardSubtitle: 'Frequency occupancy over time · newest row at top',
    connected: 'Data stream active',
    connecting: 'Connecting …',
    disconnected: 'Data stream disconnected',
    streamError: 'Data stream interrupted',
    receiver: 'Receiver',
    timeWindow: 'Time window',
    frequencyBand: 'Frequency band',
    amplitudeThreshold: 'Amplitude threshold',
    pause: 'Pause',
    resume: 'Resume',
    paused: 'Paused',
    now: 'NOW',
    frequency: 'Frequency',
    pulseDensity: 'Pulses / 100 ms',
    reduction: 'Reduction',
    latency: 'Latency',
    gapWarning: 'Data gap detected — missing rows are not interpolated.',
    staleWarning: 'Data stream delayed. Showing the most recently received data.',
    noWaterfallData: 'No waterfall tiles yet',
    noWaterfallHint: 'Check UDP input and the Kafka stream. Frequency occupancy will appear here when PDW data arrives.',
    densityLegend: 'Pulse density · logarithmic colour scale',
    aoaTitle: 'Angle of arrival (AOA)',
    aoaPending: 'Data integration pending',
    amplitude: 'Amplitude',
    language: 'Language',
    north: 'N',
    south: 'S',
    east: 'E',
    west: 'W',
    mainScreen: 'Main display',
    receiverUnavailable: 'No receiver detected yet',
    frequencyStart: 'From (GHz)',
    frequencyEnd: 'To (GHz)',
    thresholdDbm: 'dBm',
    selectedBand: 'Use selected range',
  },
} as const;

export type TranslationKey = keyof typeof translations.de;

@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly language = signal<Language>(this.readSavedLanguage());

  constructor() {
    document.documentElement.lang = this.language();
  }

  t(key: TranslationKey): string {
    return translations[this.language()][key];
  }

  setLanguage(language: Language): void {
    this.language.set(language);
    localStorage.setItem('pulse-vision-language', language);
    document.documentElement.lang = language;
  }

  private readSavedLanguage(): Language {
    const saved = localStorage.getItem('pulse-vision-language');
    return saved === 'en' ? 'en' : 'de';
  }
}
