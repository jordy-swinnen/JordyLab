import { Observable } from 'rxjs';
import { LibBotAnswer, LibBotAskEvent, LibBotStage } from './gamecatalog.models';

export interface LibBotAskRequest {
  conversationId: string;
  message: string;
  attachedGameIds: string[];
}

interface SseBlock {
  event: string;
  data: string;
}

/** Splits an SSE text buffer into complete blocks; returns the rest (an unfinished block) to keep for the next chunk. */
export function parseSseBlocks(buffer: string): { blocks: SseBlock[]; rest: string } {
  const parts = buffer.split(/\r?\n\r?\n/);
  const rest = parts.pop() ?? '';
  const blocks: SseBlock[] = [];
  for (const part of parts) {
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of part.split(/\r?\n/)) {
      if (line.startsWith(':')) {
        continue;
      }
      const separator = line.indexOf(':');
      const field = separator < 0 ? line : line.slice(0, separator);
      const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '');
      if (field === 'event') {
        event = value;
      } else if (field === 'data') {
        dataLines.push(value);
      }
    }
    if (dataLines.length > 0) {
      blocks.push({ event, data: dataLines.join('\n') });
    }
  }

  return { blocks, rest };
}

function toEvent(block: SseBlock): LibBotAskEvent | null {
  let payload: unknown;
  try {
    payload = JSON.parse(block.data);
  } catch {
    return null;
  }
  if (block.event === 'stage') {
    return { kind: 'stage', stage: (payload as { stage: LibBotStage }).stage };
  }
  if (block.event === 'answer') {
    return { kind: 'answer', answer: payload as LibBotAnswer };
  }
  if (block.event === 'error') {
    const error = payload as { code: 'UNAVAILABLE' | 'INTERNAL'; retryable: boolean };

    return { kind: 'error', code: error.code, retryable: error.retryable };
  }

  return null;
}

async function failureEvent(response: Response): Promise<LibBotAskEvent> {
  if (response.status === 429) {
    const body = (await response.json().catch(() => null)) as { resetsAt?: string } | null;

    return { kind: 'limitReached', resetsAt: body?.resetsAt ?? '' };
  }
  if (response.status >= 400 && response.status < 500) {
    return { kind: 'rejected' };
  }

  return { kind: 'error', code: 'UNAVAILABLE', retryable: true };
}

/**
 * Asks LibBot and emits its stage events, then exactly one `answer`, `error`, `limitReached` or `rejected` event.
 * Uses `fetch` because `EventSource` cannot send the bearer header or a POST body. Unsubscribing aborts the request,
 * and the server then neither remembers nor counts the question.
 */
export function askLibBot(
  request: LibBotAskRequest,
  token: string | null | undefined,
): Observable<LibBotAskEvent> {
  return new Observable<LibBotAskEvent>((subscriber) => {
    const controller = new AbortController();

    const run = async (): Promise<void> => {
      const response = await fetch('/api/gamecatalog/libbot/ask', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        subscriber.next(await failureEvent(response));
        subscriber.complete();

        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let finished = false;
      while (!finished) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const parsed = parseSseBlocks(buffer);
        buffer = parsed.rest;
        for (const block of parsed.blocks) {
          const event = toEvent(block);
          if (event) {
            subscriber.next(event);
            finished = event.kind === 'answer' || event.kind === 'error';
          }
        }
      }
      if (!finished) {
        subscriber.next({ kind: 'error', code: 'UNAVAILABLE', retryable: true });
      }
      subscriber.complete();
    };

    run().catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      subscriber.next({ kind: 'error', code: 'UNAVAILABLE', retryable: true });
      subscriber.complete();
      console.error('LibBot stream failed', error);
    });

    return () => controller.abort();
  });
}
