import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { GameExclusion } from '@jordylab-fe/gamecatalog/api';

/**
 * The admin's exclusion list (JOR-97): games hidden from the catalog. Rows can be included again; the catalog and the
 * health counts reload with each one.
 */
@Component({
  selector: 'lib-exclusions-panel',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './exclusions-panel.component.html',
})
export class ExclusionsPanelComponent {
  exclusions = input.required<GameExclusion[]>();
  loading = input.required<boolean>();
  pending = input.required<string | null>();

  include = output<string>();
}
