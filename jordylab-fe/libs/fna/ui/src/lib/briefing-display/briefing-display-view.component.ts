import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Briefing } from '@jordylab-fe/fna/api';
import { EmptyStateComponent, PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { MarkdownPipe } from './markdown.pipe';

@Component({
  selector: 'lib-briefing-display-view',
  standalone: true,
  imports: [DatePipe, HlmButtonDirective, EmptyStateComponent, PageHeaderComponent, MarkdownPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './briefing-display-view.component.html',
})
export class BriefingDisplayViewComponent {
  briefing = input.required<Briefing | null>();
  loading = input.required<boolean>();

  generate = output<void>();
}
