import { Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Language, LanguageService } from './language.service';
import { WaterfallStreamService } from './waterfall-stream.service';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly i18n = inject(LanguageService);
  protected readonly stream = inject(WaterfallStreamService);
  protected readonly connectionLabel = computed(() => {
    switch (this.stream.status()) {
      case 'connected': return this.i18n.t('connected');
      case 'connecting': return this.i18n.t('connecting');
      case 'error': return this.i18n.t('streamError');
      default: return this.i18n.t('disconnected');
    }
  });

  protected setLanguage(language: string): void {
    this.i18n.setLanguage(language as Language);
  }

  ngOnInit(): void {
    this.stream.connect();
  }
}
