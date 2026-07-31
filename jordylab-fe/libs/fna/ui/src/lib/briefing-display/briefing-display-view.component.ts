import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Briefing } from '@jordylab-fe/fna/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { MarkdownPipe } from './markdown.pipe';

@Component({
  selector: 'lib-briefing-display-view',
  standalone: true,
  imports: [DatePipe, HlmButtonDirective, MarkdownPipe],
  templateUrl: './briefing-display-view.component.html',
})
export class BriefingDisplayViewComponent {
  briefing = input.required<Briefing | null>();
  loading = input.required<boolean>();

  generate = output<void>();
}
