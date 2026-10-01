import { Pipe, PipeTransform } from '@angular/core';
import { marked } from 'marked';

/**
 * Renders markdown to an HTML string. The briefing is AI-generated from ingested RSS articles, so
 * the result is deliberately left untrusted: bind it via `[innerHTML]` and Angular's sanitizer strips
 * scripts, event handlers and unsafe URLs while keeping the markdown markup.
 */
@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }

    return marked.parse(value, { async: false }) as string;
  }
}
