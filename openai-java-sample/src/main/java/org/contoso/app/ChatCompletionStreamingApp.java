package org.contoso.app;

import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatCompletionChunk;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import java.util.HashMap;
import java.util.Map;

public final class ChatCompletionStreamingApp {
//    static {
//        configureOTEL();
//    }

    // NOTE: Stainless SDK streaming API will not currently work against Azure endpoint, so this sample will not work.
    public static void main(String[] args) throws Exception {
        final OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder();

        clientBuilder
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .credential(AzureApiKeyCredential.create(System.getenv("AZURE_OPENAI_KEY")));

        final OpenAIClient client = clientBuilder.build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addMessage(ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                        ChatCompletionUserMessageParam.builder()
                                .role(ChatCompletionUserMessageParam.Role.USER)
                                .content(ChatCompletionUserMessageParam.Content.ofTextContent("Who won the world series in 2020?"))
                                .build()))
                .model("gpt-4o")
                .temperature(0.75)
                .topP(0.5)
                .build();

        // 'createStreaming(params)' API is yet to be supported by Stainless SDK against Azure endpoint.
        final StreamResponse<ChatCompletionChunk> chatCompletionStream = client.chat().completions().createStreaming(params);
        chatCompletionStream.stream().forEach(chunk -> {
            System.out.println("Chunk: " + chunk);
        });
        chatCompletionStream.close();
    }

//    private static void configureOTEL() {
//        final AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
//        sdkBuilder
//                .addPropertiesSupplier(() -> {
//                    final Map<String, String> properties = new HashMap<>();
//                    properties.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
//                    return properties;
//                })
//                .setResultAsGlobal()
//                .build()
//                .getOpenTelemetrySdk();
//    }
}
