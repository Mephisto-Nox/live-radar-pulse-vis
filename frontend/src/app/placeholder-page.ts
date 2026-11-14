import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-placeholder-page',
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ section }}</p>
        <h1>{{ title }}</h1>
        <p class="subtitle">Die Oberfläche ist vorbereitet. Live-Daten und Analysefunktionen sind noch nicht aktiviert.</p>
      </div>
      <span class="draft-badge"><span></span> VORBEREITET</span>
    </div>

    <section class="status-card" aria-label="Funktionsstatus">
      <div class="illustration" aria-hidden="true">
        <svg viewBox="0 0 120 120" fill="none"><circle cx="60" cy="60" r="47" stroke="currentColor" stroke-opacity=".12" stroke-width="1.5"/><circle cx="60" cy="60" r="32" stroke="currentColor" stroke-opacity=".18" stroke-width="1.5"/><circle cx="60" cy="60" r="16" stroke="currentColor" stroke-opacity=".24" stroke-width="1.5"/><path d="M60 60 91 40" stroke="#2DBCA7" stroke-width="2" stroke-linecap="round"/><circle cx="60" cy="60" r="3" fill="#2DBCA7"/><circle cx="84" cy="45" r="3" fill="#2DBCA7"/></svg>
      </div>
      <h2>Bereit für den nächsten Schritt</h2>
      <p>Der Dienst läuft als technische Grundlage. Sobald Empfänger angebunden sind, erscheinen Status und Visualisierungen hier.</p>
      <div class="card-divider"></div>
      <div class="service-status"><span class="status-icon">i</span><span><strong>Keine Empfänger verbunden</strong><small>Die Anbindung wird in einem späteren Entwicklungsschritt ergänzt.</small></span><span class="status-pill">INAKTIV</span></div>
    </section>

    <div class="info-grid">
      <article class="info-card"><span class="info-icon teal">⌁</span><div><h3>Live-Daten</h3><p>Wartet auf eine konfigurierte Datenquelle.</p></div><span class="tiny-status"></span></article>
      <article class="info-card"><span class="info-icon blue">◷</span><div><h3>Analyse</h3><p>Emitter-Analyse ist noch nicht aktiviert.</p></div><span class="tiny-status"></span></article>
      <article class="info-card"><span class="info-icon violet">▤</span><div><h3>Visualisierung</h3><p>Diagramme werden später mit Live-Daten gefüllt.</p></div><span class="tiny-status"></span></article>
    </div>
    <footer class="page-footer"><span>Pulse Vision · Entwicklungsumgebung</span><span>Keine Daten werden verarbeitet oder gespeichert</span></footer>
  `,
  styles: [`
    :host { display: block; }
    .page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 30px; }
    .eyebrow { margin: 0 0 10px; color: #638093; font-size: 10px; font-weight: 700; letter-spacing: .16em; }
    h1 { margin: 0; color: #172437; font-family: Manrope, 'Segoe UI', sans-serif; font-size: clamp(25px, 3vw, 33px); font-weight: 700; letter-spacing: -.04em; }
    .subtitle { margin: 10px 0 0; color: #7c8998; font-size: 13px; line-height: 1.65; }
    .draft-badge { display: inline-flex; align-items: center; gap: 7px; padding: 8px 10px; border: 1px solid #e8ebee; border-radius: 6px; background: white; color: #7c8998; font-size: 9px; font-weight: 700; letter-spacing: .08em; white-space: nowrap; }
    .draft-badge span { width: 6px; height: 6px; border-radius: 50%; background: #d7a35a; }
    .status-card { display: flex; flex-direction: column; align-items: center; padding: 35px 36px 0; border: 1px solid #e8edf2; border-radius: 12px; background: white; text-align: center; box-shadow: 0 4px 18px rgba(27,45,68,.025); }
    .illustration { display: grid; width: 94px; height: 94px; place-items: center; border-radius: 50%; background: #f3faf9; color: #53b9a9; }
    .illustration svg { width: 84px; height: 84px; }
    h2 { margin: 19px 0 8px; color: #243248; font-family: Manrope, 'Segoe UI', sans-serif; font-size: 18px; font-weight: 700; letter-spacing: -.02em; }
    .status-card > p { max-width: 470px; margin: 0; color: #7d8999; font-size: 12px; line-height: 1.7; }
    .card-divider { width: calc(100% + 72px); height: 1px; margin-top: 27px; background: #edf0f3; }
    .service-status { display: flex; width: 100%; align-items: center; gap: 12px; padding: 16px 0; text-align: left; }
    .status-icon { display: grid; width: 29px; height: 29px; flex: 0 0 auto; place-items: center; border-radius: 8px; background: #f2f5f8; color: #8290a1; font-family: Georgia, serif; font-size: 15px; font-style: italic; }
    .service-status strong, .service-status small { display: block; }
    .service-status strong { color: #405065; font-size: 11px; font-weight: 600; }
    .service-status small { margin-top: 4px; color: #929eac; font-size: 10px; }
    .status-pill { margin-left: auto; padding: 5px 7px; border-radius: 4px; background: #f6f3ee; color: #9c8058; font-size: 8px; font-weight: 700; letter-spacing: .08em; }
    .info-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; margin-top: 16px; }
    .info-card { display: flex; min-height: 86px; align-items: flex-start; gap: 12px; padding: 17px 15px; border: 1px solid #e8edf2; border-radius: 10px; background: white; }
    .info-icon { display: grid; width: 30px; height: 30px; flex: 0 0 auto; place-items: center; border-radius: 8px; font-size: 17px; }
    .info-icon.teal { background: #ecf8f6; color: #37a995; }
    .info-icon.blue { background: #eef4fc; color: #658fc5; }
    .info-icon.violet { background: #f4f1fb; color: #8976bc; }
    .info-card h3 { margin: 2px 0 5px; color: #3a4a5f; font-size: 11px; font-weight: 700; }
    .info-card p { margin: 0; color: #8a97a6; font-size: 10px; line-height: 1.5; }
    .tiny-status { width: 6px; height: 6px; flex: 0 0 auto; margin: 4px 0 0 auto; border-radius: 50%; background: #d9e0e7; }
    .page-footer { display: flex; justify-content: space-between; gap: 14px; margin-top: 24px; color: #9aa5b1; font-size: 9px; }
    @media (max-width: 800px) { .info-grid { grid-template-columns: 1fr; } }
    @media (max-width: 520px) { .page-heading { flex-direction: column; } .status-card { padding: 26px 18px 0; } .card-divider { width: calc(100% + 36px); } .service-status small { max-width: 210px; line-height: 1.4; } .page-footer { flex-direction: column; } }
  `],
})
export class PlaceholderPage {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = this.route.snapshot.data['title'] as string;
  protected readonly section = this.route.snapshot.data['section'] as string;
}
