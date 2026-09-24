package com.claimpilot.cache;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import com.claimpilot.config.AppProperties;

/**
 * A model call that is made once per distinct prompt and user: repeat questions and repeat claim
 * guides come back from the cache in milliseconds instead of seconds. The model's name is part of
 * the key, so switching models (Ollama to Bedrock, or another version) never serves old replies.
 */
@Component
public class CachedModel {

    private final ChatClient chatClient;
    private final ModelCache cache;
    private final AppProperties properties;
    private final String modelId;

    public CachedModel(ChatClient.Builder builder, ChatModel chatModel, ModelCache cache, AppProperties properties) {
        this.chatClient = builder.build();
        this.cache = cache;
        this.properties = properties;
        this.modelId = modelId(chatModel);
    }

    public String complete(Long ownerId, String system, String user) {
        String key = cacheKey(modelId, system, user);
        return cache.get(ownerId, key).orElseGet(() -> {
            String reply = system == null
                    ? chatClient.prompt().user(user).call().content()
                    : chatClient.prompt().system(system).user(user).call().content();
            if (reply != null && !reply.isBlank()) {
                cache.put(ownerId, key, reply, properties.cache().ttl());
            }
            return reply;
        });
    }

    static String cacheKey(String modelId, String system, String user) {
        return ModelCache.keyOf(modelId, system, user);
    }

    /** The configured model, for example "qwen3:8b"; the implementation's name when none is set. */
    static String modelId(ChatModel chatModel) {
        ChatOptions options = chatModel.getDefaultOptions();
        String model = options == null ? null : options.getModel();
        return chatModel.getClass().getSimpleName() + ":" + (model == null ? "default" : model);
    }
}
