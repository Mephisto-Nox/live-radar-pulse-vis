import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'pulse-vision-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.readInitialTheme());

  private readonly systemTheme = window.matchMedia('(prefers-color-scheme: dark)');

  constructor() {
    this.applyTheme(this.theme());
    this.systemTheme.addEventListener('change', this.handleSystemThemeChange);
  }

  toggle(): void {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
    this.applyTheme(theme);
  }

  private readonly handleSystemThemeChange = (event: MediaQueryListEvent): void => {
    if (localStorage.getItem(THEME_STORAGE_KEY) !== null) return;
    const theme = event.matches ? 'dark' : 'light';
    this.theme.set(theme);
    this.applyTheme(theme);
  };

  private readInitialTheme(): Theme {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    if (savedTheme === 'light' || savedTheme === 'dark') return savedTheme;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.dataset['theme'] = theme;
    document.documentElement.style.colorScheme = theme;
  }
}
