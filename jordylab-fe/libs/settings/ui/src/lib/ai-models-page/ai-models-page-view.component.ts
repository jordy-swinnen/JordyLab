import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AiFeatureModel, CatalogModel, ModelCatalog, VendorGroup } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

@Component({
  selector: 'lib-ai-models-page-view',
  standalone: true,
  imports: [DatePipe, DecimalPipe, HlmBadgeDirective, HlmButtonDirective, HlmSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './ai-models-page-view.component.html',
})
export class AiModelsPageViewComponent {
  features = input.required<AiFeatureModel[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  pickerFeature = input.required<AiFeatureModel | null>();
  catalog = input.required<ModelCatalog | null>();
  catalogError = input.required<string | null>();
  vendorGroups = input.required<VendorGroup[]>();
  search = input.required<string>();
  saving = input.required<boolean>();
  saveError = input.required<string | null>();

  openPicker = output<string>();
  closePicker = output<void>();
  searchChange = output<string>();
  choose = output<string>();

  price(model: CatalogModel): string {
    const { input: inputPrice, output: outputPrice } = model.pricingPerMillionTokens;
    if (inputPrice === null || outputPrice === null) {
      return 'router-priced';
    }

    return `$${inputPrice} in · $${outputPrice} out / 1M tokens`;
  }

  lastRunLine(feature: AiFeatureModel): string {
    const run = feature.lastRun;
    if (!run) {
      return 'Not run yet';
    }
    const via = run.fallbackUsed ? ` via fallback (${run.provider})` : ` via ${run.provider}`;

    return run.outcome === 'SUCCESS'
      ? `Last run succeeded${via} · ${run.model}`
      : `Last run failed${via} · ${run.failureReason ?? 'unknown reason'}`;
  }
}
