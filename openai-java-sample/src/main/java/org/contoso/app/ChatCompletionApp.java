package org.contoso.app;

import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;

import java.util.List;

public final class ChatCompletionApp {
//    static {
//        configureOTEL();
//    }

    public static void main(String[] args) {
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

        final ChatCompletion chatCompletion = client.chat().completions().create(params);

        final List<ChatCompletion.Choice> choices = chatCompletion.choices();
        for (ChatCompletion.Choice choice : choices) {
            System.out.println("Choice content: " + choice.message().content().get());
        }

        final JsonValue filterResult = chatCompletion._additionalProperties().get("prompt_filter_results");
        System.out.println("Content filter results: " + filterResult);
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
