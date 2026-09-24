package com.companybrain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void snippetDropsHeadingAndFlattensWhitespace() {
        assertThat(ChatService.snippet("## Vacation\nYou get\n  15 days.")).isEqualTo("You get 15 days.");
    }

    @Test
    void snippetIsTruncatedWithEllipsis() {
        String snippet = ChatService.snippet("word ".repeat(200));

        assertThat(snippet).hasSize(321).endsWith("…");
    }
}
