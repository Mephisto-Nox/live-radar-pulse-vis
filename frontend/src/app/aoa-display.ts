import { Component, inject } from '@angular/core';
import { LanguageService } from './language.service';

@Component({
  selector: 'app-aoa-display',
  styleUrl: './aoa-display.scss',
  templateUrl: './aoa-display.html',
})
export class AoaDisplay {
  protected readonly i18n = inject(LanguageService);
}
