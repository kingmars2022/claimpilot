package com.companybrain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

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

    @Test
    void mergeKeepsBestScorePerChunkInOrder() {
        Document a1 = Document.builder().id("a").text("a").score(0.5).build();
        Document a2 = Document.builder().id("a").text("a").score(0.9).build();
        Document b = Document.builder().id("b").text("b").score(0.7).build();
        Document c = Document.builder().id("c").text("c").score(0.3).build();

        List<Document> merged = ChatService.mergeByScore(List.of(a1, c), List.of(a2, b), 2);

        assertThat(merged).extracting(Document::getId).containsExactly("a", "b");
        assertThat(merged.getFirst().getScore()).isEqualTo(0.9);
    }
}
