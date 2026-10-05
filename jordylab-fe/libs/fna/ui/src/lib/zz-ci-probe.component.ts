import { Component } from '@angular/core';
import * as gamecatalogApi from '@jordylab-fe/gamecatalog/api';

@Component({
  selector: 'lib-ci-probe',
  standalone: true,
  template: `<img src="probe.png" />`,
})
export class CiProbeComponent {
  readonly api = gamecatalogApi;
}
