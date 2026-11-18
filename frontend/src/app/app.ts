import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Language, LanguageService } from './language.service';
import { ThemeService } from './theme.service';
import { WaterfallStreamService } from './waterfall-stream.service';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit, OnDestroy {
  protected readonly i18n = inject(LanguageService);
  protected readonly theme = inject(ThemeService);
  protected readonly stream = inject(WaterfallStreamService);
  protected setLanguage(language: string): void {
    this.i18n.setLanguage(language as Language);
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  ngOnInit(): void {
    this.stream.startHealthMonitoring();
  }

  ngOnDestroy(): void {
    this.stream.stopHealthMonitoring();
  }
}
