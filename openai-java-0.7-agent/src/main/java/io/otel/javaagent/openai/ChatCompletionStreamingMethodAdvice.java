package io.otel.javaagent.openai;

import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatCompletionChunk;
import com.openai.models.ChatCompletionCreateParams;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import net.bytebuddy.asm.Advice;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ChatCompletionStreamingMethodAdvice {
    public static Tracer tracer = GlobalOpenTelemetry.getTracer("io.otel.javaagent.openai");

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ChatCompletionCreateParams params,
        @Advice.Argument(value = 1, readOnly = false) RequestOptions requestOptions) {
        Span span = tracer.spanBuilder("chat " + params.model()).startSpan();
        setRequestAttributes(span, params);
        span.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) StreamResponse<ChatCompletionChunk> response,
        @Advice.Thrown Throwable throwable) {
        Span span = Span.current();
        if (throwable != null) {
            span.setAttribute("error.type", throwable.getClass().getName());
            span.recordException(throwable);
        } else {
            response = new TracedStreamResponse(response, span);
        }
        span.end();
    }

    public static void setRequestAttributes(Span span, ChatCompletionCreateParams params) {
        span.setAttribute("gen_ai.operation.name", "chat");
        span.setAttribute("gen_ai.system", "openai.inference");
        span.setAttribute("gen_ai.request.model", String.valueOf(params.model()));
        params.maxCompletionTokens().ifPresent(value -> span.setAttribute("gen_ai.request.max_tokens", value));
        params.temperature().ifPresent(value -> span.setAttribute("gen_ai.request.temperature", value));
        params.topP().ifPresent(value -> span.setAttribute("gen_ai.request.top_p", value));
    }

    public static final class TracedStreamResponse implements StreamResponse<ChatCompletionChunk> {
        private final AtomicBoolean spanEnded = new AtomicBoolean(false);
        private final StreamResponse<ChatCompletionChunk> inner;
        private final Span span;
        private final List<ChatCompletionChunk.Choice> choices = new ArrayList<>();
        private ChatCompletionChunk lastChunk;

        public TracedStreamResponse(StreamResponse<ChatCompletionChunk> inner, Span span) {
            this.inner = inner;
            this.span = span;
        }

        @NotNull
        @Override
        public Stream<ChatCompletionChunk> stream() {
            return inner.stream()
                    .map(chunk -> {
                        this.choices.addAll(chunk.choices());
                        lastChunk = chunk;
                        return chunk;
                    })
                    .onClose(this::endSpan);
        }

        @Override
        public void close() throws Exception {
            try {
                inner.close();
            } finally {
                endSpan();
            }
        }

        private void endSpan() {
            if (spanEnded.getAndSet(true)) {
                return;
            }
            lastChunk._id().asString().ifPresent(value -> span.setAttribute("gen_ai.response.id", value));
            lastChunk._model().asString().ifPresent(value -> span.setAttribute("gen_ai.response.model", value));
            lastChunk.usage().ifPresent(usage -> {
                span.setAttribute("gen_ai.usage.input_tokens", usage.promptTokens());
                span.setAttribute("gen_ai.usage.output_tokens", usage.completionTokens());
            });
            final StringJoiner finishReasons = new StringJoiner(",", "[", "]");
            for (ChatCompletionChunk.Choice choice : choices) {
                finishReasons.add(choice.finishReason().toString());
            }
            span.setAttribute("gen_ai.response.finish_reasons", finishReasons.toString());
            span.end();
        }
    }
}
