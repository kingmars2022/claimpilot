package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class KeywordSearchTest {

    @Test
    void tsvectorTextIsReadAsWordCounts() {
        assertThat(KeywordSearch.lexemes("'crown':5 'major':3,8A 'o''brien':2"))
                .isEqualTo(Map.of("crown", 1, "major", 2, "o'brien", 1));
        assertThat(KeywordSearch.lexemes("")).isEmpty();
        assertThat(KeywordSearch.lexemes(null)).isEmpty();
    }

    @Test
    void aRareWordOutweighsWordsFoundOnEveryPage() {
        List<KeywordSearch.Chunk> chunks = List.of(
                chunk("claims", Map.of("claim", 3, "plan", 2, "submit", 1)),
                chunk("dental", Map.of("crown", 1, "plan", 1, "major", 1, "coverag", 1)),
                chunk("health", Map.of("claim", 1, "plan", 1, "physiotherapist", 1)));

        List<Document> ranked = KeywordSearch.rank(Set.of("claim", "crown", "join", "plan"), chunks, 5);

        assertThat(ranked).extracting(Document::getId).first().isEqualTo("dental");
    }

    @Test
    void chunksWithoutAnyWordAreLeftOut() {
        List<KeywordSearch.Chunk> chunks = List.of(
                chunk("a", Map.of("massag", 1)),
                chunk("b", Map.of("dental", 2)));

        assertThat(KeywordSearch.rank(Set.of("botox"), chunks, 5)).isEmpty();
        assertThat(KeywordSearch.rank(Set.of("dental"), chunks, 5)).extracting(Document::getId).containsExactly("b");
        assertThat(KeywordSearch.rank(Set.of("dental"), List.of(), 5)).isEmpty();
    }

    @Test
    void oneSharedWordOutOfFourIsNotAMatch() {
        List<KeywordSearch.Chunk> chunks = List.of(chunk("chiro", Map.of("chiropractor", 1, "includ", 1, "x-ray", 1)));

        assertThat(KeywordSearch.rank(Set.of("laser", "eye", "surgeri", "includ"), chunks, 5)).isEmpty();
    }

    private static KeywordSearch.Chunk chunk(String id, Map<String, Integer> words) {
        return new KeywordSearch.Chunk(Document.builder().id(id).text(id).build(), words,
                words.values().stream().mapToInt(Integer::intValue).sum());
    }
}
