package com.claimpilot.cache;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.claimpilot.config.AppProperties;

/**
 * A model call that is made once per distinct prompt and user: repeat questions and repeat claim
 * guides come back from the cache in milliseconds instead of seconds.
 */
@Component
public class CachedModel {

    private final ChatClient chatClient;
    private final ModelCache cache;
    private final AppProperties properties;

    public CachedModel(ChatClient.Builder builder, ModelCache cache, AppProperties properties) {
        this.chatClient = builder.build();
        this.cache = cache;
        this.properties = properties;
    }

    public String complete(Long ownerId, String system, String user) {
        String key = ModelCache.keyOf(system, user);
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
}
