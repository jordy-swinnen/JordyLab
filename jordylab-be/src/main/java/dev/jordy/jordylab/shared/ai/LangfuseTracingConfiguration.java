package dev.jordy.jordylab.shared.ai;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Sends AI traces to Langfuse over OTLP/HTTP when both keys are set; otherwise tracing exports nothing. */
@Configuration(proxyBeanMethods = false)
class LangfuseTracingConfiguration {

    @Bean
    @ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText('${langfuse.public-key:}')"
                    + " and T(org.springframework.util.StringUtils).hasText('${langfuse.secret-key:}')")
    SpanExporter langfuseSpanExporter(LangfuseProperties properties) {
        String credentials = Base64.getEncoder().encodeToString(
                (properties.publicKey() + ":" + properties.secretKey()).getBytes(StandardCharsets.UTF_8));
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.tracesEndpoint())
                .addHeader("Authorization", "Basic " + credentials)
                .addHeader("x-langfuse-ingestion-version", "4")
                .build();

        return new AiSpanExporter(exporter);
    }

    @Bean
    LangfuseObservationFilter langfuseObservationFilter(LangfuseProperties properties) {
        return new LangfuseObservationFilter(properties.captureContent());
    }
}
