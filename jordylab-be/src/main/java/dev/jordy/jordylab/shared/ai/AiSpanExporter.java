package dev.jordy.jordylab.shared.ai;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

/**
 * Passes only AI spans on to Langfuse: those carrying a {@code gen_ai.*} (Spring AI) or {@code langfuse.*} (ours)
 * attribute. HTTP server, Modulith and other framework spans are left out so a trace shows what the model was asked,
 * not the plumbing around it.
 */
@RequiredArgsConstructor
final class AiSpanExporter implements SpanExporter {

    private final SpanExporter delegate;

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> aiSpans = spans.stream().filter(AiSpanExporter::isAiSpan).toList();
        if (aiSpans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }

        return delegate.export(aiSpans);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    static boolean isAiSpan(SpanData span) {
        return span.getAttributes().asMap().keySet().stream()
                .anyMatch(key -> key.getKey().startsWith("gen_ai.") || key.getKey().startsWith("langfuse."));
    }
}
