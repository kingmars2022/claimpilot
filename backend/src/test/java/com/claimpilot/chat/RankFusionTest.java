package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RankFusionTest {

    @Test
    void aChunkFoundByBothSearchesComesFirst() {
        List<Document> vector = List.of(doc("health", 0.62), doc("claims", 0.55), doc("dental", 0.45));
        List<Document> keyword = List.of(doc("dental", null), doc("exclusions", null));

        List<Document> fused = ChatService.fuse(List.of(vector, keyword), 3);

        assertThat(fused).extracting(Document::getId).containsExactly("dental", "health", "claims");
        assertThat(fused.getFirst().getScore()).as("the similarity score is kept for display").isEqualTo(0.45);
    }

    @Test
    void aChunkOnlyTheKeywordSearchFoundIsStillKept() {
        List<Document> fused = ChatService.fuse(List.of(List.of(), List.of(doc("crown", null))), 5);

        assertThat(fused).extracting(Document::getId).containsExactly("crown");
    }

    private static Document doc(String id, Double score) {
        return Document.builder().id(id).text(id).score(score).build();
    }
}
