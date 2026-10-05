import { Component, ElementRef, viewChild, effect, ChangeDetectionStrategy } from '@angular/core';
import * as QRCode from 'qrcode';

/**
 * Desktop-only "Get the Android app" user-menu entry (spec US1-6): a QR code pointing at the
 * website itself, so a phone camera opens the same install flow a phone browser would show
 * directly. Always available — never dismissed, unlike the install dialog/sheet.
 */
@Component({
  selector: 'lib-android-app-qr-entry',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="jordylab-qr-entry">
      <p>Get the JordyLab app</p>
      <canvas #canvas width="160" height="160"></canvas>
      <p class="jordylab-qr-entry__hint">Scan with your Android phone's camera</p>
    </div>
  `,
})
export class AndroidAppQrEntryComponent {
  // Angular's viewChild() signal API requires TS visibility (private/protected), not JS #field —
  // an exception to this repo's usual #field convention (Angular 21 constraint, NG1053).
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    effect(() => {
      const canvasElement = this.canvas().nativeElement;
      void QRCode.toCanvas(canvasElement, window.location.origin, { width: 160 });
    });
  }
}
