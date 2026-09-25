package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.claimpilot.document.ProcessingService;
import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.search.KeywordSearch;
import com.claimpilot.support.IntegrationTestBase;

/** Keyword search against real pgvector: stored stems, the GIN index, and the owner filter. */
class HybridSearchIntegrationTest extends IntegrationTestBase {

    @Autowired
    private KeywordSearch keywords;
    @Autowired
    private JdbcTemplate jdbc;

    private Long owner;
    private UUID booklet;

    @BeforeEach
    void uploadBooklet() throws Exception {
        String token = login("sam");
        booklet = UUID.fromString(upload(token, "policies", SampleDocuments.BOOKLET_POLICY,
                SampleDocuments.bookletPolicy()));
        owner = jdbc.queryForObject("SELECT id FROM users WHERE username = 'sam'", Long.class);
    }

    @Test
    void theStemsAreStoredAndIndexed() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN "
                + "('vector_store_stems_idx', 'vector_store_document_idx')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stems::text FROM vector_store WHERE metadata->>'documentId' = ? "
                + "AND content LIKE '%crowns%'", String.class, booklet.toString())).contains("'crown'");
    }

    @Test
    void anExactTermFindsItsPage() {
        List<Document> found = keywords.search("When can I claim a crown after joining the plan?", owner, booklet, 5);

        assertThat(found).isNotEmpty();
        assertThat(found.getFirst().getMetadata().get(ProcessingService.META_PAGE)).isEqualTo(4);
    }

    @Test
    void quotesAndHyphensInAQuestionAreJustWords() {
        assertThat(keywords.search("What's the plan's limit for an x-ray? 'crown' | ! & (", owner, booklet, 5))
                .isNotNull();
        assertThat(keywords.search("the and of", owner, booklet, 5)).as("only stop words").isEmpty();
    }

    @Test
    void aFrenchQuestionIsStemmedWithFrenchRules() throws Exception {
        String token = login("sam");
        UUID french = UUID.fromString(upload(token, "policies", SampleDocuments.OWN_POLICY,
                SampleDocuments.ownPolicyFrench()));

        List<Document> found = keywords.search("Les massothérapies sont-elles remboursées ?", owner, french, 5);

        assertThat(found).isNotEmpty();
        assertThat(found.getFirst().getText()).contains("massothérapie");
        assertThat(jdbc.queryForObject("SELECT stems::text FROM vector_store WHERE metadata->>'documentId' = ? "
                + "AND content LIKE '%massothérapie%'", String.class, french.toString()))
                .contains("'massothérap'").contains("'massothérapi'");
    }

    @Test
    void aCoverageQuestionGetsTheExclusions() {
        List<Document> exclusions = keywords.exclusions("Is Botox for wrinkles covered?", owner, booklet, 2);

        assertThat(exclusions).extracting(d -> d.getMetadata().get(ProcessingService.META_PAGE)).contains(5);
        assertThat(keywords.exclusions("What is the deadline to submit a claim?", owner, booklet, 2)).isEmpty();
    }

    @Test
    void anotherUsersPolicyIsNeverSearched() {
        Long otherUser = jdbc.queryForObject("SELECT id FROM users WHERE username = 'fiona'", Long.class);

        assertThat(keywords.search("crown dental coverage", otherUser, booklet, 5)).isEmpty();
        assertThat(keywords.exclusions("Is Botox covered?", otherUser, booklet, 2)).isEmpty();
    }
}
