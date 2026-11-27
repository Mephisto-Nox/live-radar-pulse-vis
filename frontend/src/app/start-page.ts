import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageService } from './language.service';
import { WaterfallStreamService } from './waterfall-stream.service';

@Component({
  selector: 'app-start-page',
  imports: [RouterLink],
  styleUrl: './start-page.scss',
  templateUrl: './start-page.html',
})
export class StartPage {
  protected readonly i18n = inject(LanguageService);
  protected readonly stream = inject(WaterfallStreamService);
}
