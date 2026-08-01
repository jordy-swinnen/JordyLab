import { Component, inject, OnInit, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { FnaApiService, Briefing } from '@jordylab-fe/fna/api';
import { BriefingDisplayViewComponent } from './briefing-display-view.component';

@Component({
  selector: 'lib-briefing-display',
  standalone: true,
  imports: [BriefingDisplayViewComponent],
  templateUrl: './briefing-display.component.html',
})
export class BriefingDisplayComponent implements OnInit {
  #api = inject(FnaApiService);

  briefing = signal<Briefing | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.#api
      .getLatestBriefing()
      .pipe(
        catchError(() => {
          this.error.set('Failed to load briefing.');
          this.loading.set(false);
          return of(null);
        })
      )
      .subscribe((briefing) => {
        this.briefing.set(briefing);
        this.loading.set(false);
      });
  }

  generate(): void {
    this.loading.set(true);
    this.error.set(null);
    this.#api
      .triggerBriefing()
      .pipe(
        catchError(() => {
          this.error.set('Failed to generate briefing.');
          this.loading.set(false);
          return of(null);
        })
      )
      .subscribe((briefing) => {
        this.briefing.set(briefing);
        this.loading.set(false);
      });
  }
}
