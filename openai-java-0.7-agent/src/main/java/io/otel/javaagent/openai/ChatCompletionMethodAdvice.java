package io.otel.javaagent.openai;

import com.openai.core.RequestOptions;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import net.bytebuddy.asm.Advice;

import java.util.List;
import java.util.StringJoiner;

public class ChatCompletionMethodAdvice {
    public static Tracer tracer = GlobalOpenTelemetry.getTracer("io.otel.javaagent.openai");

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ChatCompletionCreateParams params,
        @Advice.Argument(value = 1, readOnly = false) RequestOptions requestOptions) {
         Span span = tracer.spanBuilder("chat " + params.model()).startSpan();
         setRequestAttributes(span, params);
         span.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return ChatCompletion response, @Advice.Thrown Throwable throwable) {
        Span span = Span.current();
        if (throwable != null) {
            span.setAttribute("error.type", throwable.getClass().getName());
            span.recordException(throwable);
        } else {
            setResponseAttributes(span, response);
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

    public static void setResponseAttributes(Span span, ChatCompletion response) {
        response._id().asString().ifPresent(value -> span.setAttribute("gen_ai.response.id", value));
        response._model().asString().ifPresent(value -> span.setAttribute("gen_ai.response.model", value));
        response.usage().ifPresent(usage -> {
            span.setAttribute("gen_ai.usage.input_tokens", usage.promptTokens());
            span.setAttribute("gen_ai.usage.output_tokens", usage.completionTokens());
        });
        final StringJoiner finishReasons = new StringJoiner(",", "[", "]");
        final List<ChatCompletion.Choice> choices = response.choices();
        for (ChatCompletion.Choice choice : choices) {
            finishReasons.add(choice.finishReason().toString());
        }
        span.setAttribute("gen_ai.response.finish_reasons", finishReasons.toString());
    }
}