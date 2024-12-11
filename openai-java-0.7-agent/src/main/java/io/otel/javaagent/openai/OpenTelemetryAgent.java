package io.otel.javaagent.openai;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class OpenTelemetryAgent {
    static {
        // This is just to speedup prototyping, the OTEL should never be configured by agent but by the application.
        configureOTEL();
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
                // .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .type(ElementMatchers.named("com.openai.services.blocking.chat.CompletionServiceImpl"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder.visit(Advice.to(ChatCompletionMethodAdvice.class)
                            .on(isMethod()
                                    .and(isPublic())
                                    .and(named("create"))
                                    .and(takesArgument(0, named("com.openai.models.ChatCompletionCreateParams")))
                                    .and(takesArgument(1, named("com.openai.core.RequestOptions")))
                                    .and(returns(named("com.openai.models.ChatCompletion")))));
                })
                .installOn(inst);
    }

    private static void configureOTEL() {
        final AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        sdkBuilder
                .setResultAsGlobal()
                .build()
                .getOpenTelemetrySdk();
    }
}