import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemeStore } from '@jordylab-fe/shared/theme/api';
import { ThemePickerViewComponent } from './theme-picker-view.component';

@Component({
  selector: 'app-theme-picker',
  standalone: true,
  imports: [ThemePickerViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-theme-picker-view [themes]="store.themes" [preference]="store.preference()" (chosen)="store.choose($event)" />`,
})
export class ThemePickerComponent {
  protected readonly store = inject(ThemeStore);
}
