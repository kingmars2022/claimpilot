package com.claimpilot.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class CachedModelTest {

    @Test
    void theSamePromptOnAnotherModelIsANewCacheEntry() {
        String qwen = CachedModel.modelId(model("qwen3:8b"));
        String claude = CachedModel.modelId(model("anthropic.claude-haiku-4-5"));

        assertThat(qwen).contains("qwen3:8b");
        assertThat(CachedModel.cacheKey(qwen, "system", "Is physio covered?"))
                .isNotEqualTo(CachedModel.cacheKey(claude, "system", "Is physio covered?"))
                .isEqualTo(CachedModel.cacheKey(qwen, "system", "Is physio covered?"));
    }

    private static ChatModel model(String name) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ChatOptions.builder().model(name).build();
            }
        };
    }
}
