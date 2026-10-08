import { ReadableStream as NodeReadableStream } from 'node:stream/web';
import { firstValueFrom, toArray } from 'rxjs';
import { LibBotAskEvent } from './gamecatalog.models';
import { askLibBot, parseSseBlocks } from './libbot-stream';
import { aLibBotAnswerMock } from './mocks/libbot-answer.model.mock';

const REQUEST = { conversationId: 'c-1', message: 'hello', attachedGameIds: [] };

/** The jsdom test environment has no ReadableStream; Node's web stream is what a real fetch body is. */
function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();

  return new NodeReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  }) as unknown as ReadableStream<Uint8Array>;
}

function respondWith(body: ReadableStream<Uint8Array> | null, init: ResponseInit = { status: 200 }) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(body, { ...init, headers: { 'Content-Type': 'text/event-stream' } })),
  );
}

describe('parseSseBlocks', () => {
  it('returns complete blocks and keeps the unfinished tail', () => {
    const { blocks, rest } = parseSseBlocks('event:stage\ndata:{"a":1}\n\nevent:answer\ndata:{"b"');

    expect(blocks).toEqual([{ event: 'stage', data: '{"a":1}' }]);
    expect(rest).toBe('event:answer\ndata:{"b"');
  });

  it('accepts a space after the colon, ignores comment heartbeats and joins multi-line data', () => {
    const { blocks } = parseSseBlocks(': ping\n\nevent: stage\ndata: line one\ndata: line two\n\n');

    expect(blocks).toEqual([{ event: 'stage', data: 'line one\nline two' }]);
  });
});

describe('askLibBot', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('emits the stages in order and then the answer, sending the bearer token and the body', async () => {
    const answer = aLibBotAnswerMock();
    respondWith(
      streamOf(
        'event:stage\ndata:{"stage":"UNDERSTANDING"}\n\n',
        'event:stage\ndata:{"stage":"SEARCHING"}\n\n: ping\n\nevent:answer\ndata:' + JSON.stringify(answer) + '\n\n',
      ),
    );

    const events = await firstValueFrom(askLibBot(REQUEST, 'token-1').pipe(toArray()));

    expect(events).toEqual<LibBotAskEvent[]>([
      { kind: 'stage', stage: 'UNDERSTANDING' },
      { kind: 'stage', stage: 'SEARCHING' },
      { kind: 'answer', answer },
    ]);
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe('/api/gamecatalog/libbot/ask');
    expect((init?.headers as Record<string, string>)['Authorization']).toBe('Bearer token-1');
    expect(JSON.parse(init?.body as string)).toEqual(REQUEST);
  });

  it('reassembles an event split across chunks', async () => {
    respondWith(streamOf('event:stage\nda', 'ta:{"stage":"WRITING"}\n', '\n'));

    const events = await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()));

    expect(events[0]).toEqual({ kind: 'stage', stage: 'WRITING' });
  });

  it('maps an error event', async () => {
    respondWith(streamOf('event:error\ndata:{"code":"UNAVAILABLE","retryable":true}\n\n'));

    const events = await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()));

    expect(events).toEqual([{ kind: 'error', code: 'UNAVAILABLE', retryable: true }]);
  });

  it('maps a 429 to the limit-reached state with the reset time', async () => {
    respondWith(streamOf(JSON.stringify({ reason: 'CHAT_LIMIT_REACHED', resetsAt: '2026-10-08T00:00:00Z' })), {
      status: 429,
    });

    const events = await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()));

    expect(events).toEqual([{ kind: 'limitReached', resetsAt: '2026-10-08T00:00:00Z' }]);
  });

  it('maps other client errors to rejected and server errors to unavailable', async () => {
    respondWith(streamOf('{}'), { status: 400 });
    expect(await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()))).toEqual([{ kind: 'rejected' }]);

    respondWith(streamOf('{}'), { status: 502 });
    expect(await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()))).toEqual([
      { kind: 'error', code: 'UNAVAILABLE', retryable: true },
    ]);
  });

  it('reports an unavailable error when the stream ends without an answer', async () => {
    respondWith(streamOf('event:stage\ndata:{"stage":"SEARCHING"}\n\n'));

    const events = await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()));

    expect(events.at(-1)).toEqual({ kind: 'error', code: 'UNAVAILABLE', retryable: true });
  });

  it('reports an unavailable error when the network fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network down')));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const events = await firstValueFrom(askLibBot(REQUEST, null).pipe(toArray()));

    expect(events).toEqual([{ kind: 'error', code: 'UNAVAILABLE', retryable: true }]);
  });

  it('aborts the request when unsubscribed', () => {
    let signal: AbortSignal | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((_url: string, init: RequestInit) => {
        signal = init.signal as AbortSignal;

        return new Promise(() => undefined);
      }),
    );

    askLibBot(REQUEST, null).subscribe().unsubscribe();

    expect(signal?.aborted).toBe(true);
  });
});
