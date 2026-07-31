import { Component, inject, OnInit, signal } from '@angular/core';
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

  ngOnInit(): void {
    this.#api
      .getLatestBriefing()
      .subscribe((briefing) => this.briefing.set(briefing));
  }

  generate(): void {
    this.loading.set(true);
    this.#api.triggerBriefing().subscribe((briefing) => {
      this.briefing.set(briefing);
      this.loading.set(false);
    });
  }
}
