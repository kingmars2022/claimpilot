package com.claimpilot.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.claimpilot.document.OwnerScope;
import com.claimpilot.document.ProcessingService;

/**
 * Keyword search over one policy's chunks, next to the vector search. Embeddings miss exact
 * terms ("crown", "predetermination") when a chunk talks about several things at once; a word
 * match finds them. Postgres stems the words (crowns → crown) and the chunks are ranked with
 * BM25, so words that appear on every page ("claim", "plan") count for little. A chunk must
 * hold at least half of the question's words: one shared word ("included") is not a match.
 * <p>
 * A policy has tens to a few hundred chunks, so they are scored in memory, always filtered by
 * owner and policy like the vector search.
 */
@Component
public class KeywordSearch {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern LEXEME = Pattern.compile("'((?:[^']|'')+)':([0-9A-D,]+)");
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** Wording of an exclusions clause, which decides every "is X covered?" question. */
    private static final Pattern EXCLUSION = Pattern.compile(
            "(?i)exclusion|not covered|does not cover|not eligible|not reimbursed|exclu|non couvert|ne couvre pas");
    private static final Pattern COVERAGE_QUESTION = Pattern.compile(
            "(?i)\\bcover|\\breimburs|\\bpay for\\b|\\beligible\\b|\\bclaim for\\b|rembours|couvert|保不保|报销|赔");

    private final JdbcTemplate jdbc;

    public KeywordSearch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The best matching chunks for the question, best first. Empty when no word matches. */
    public List<Document> search(String question, Long ownerId, UUID policyId, int limit) {
        Set<String> terms = lexemes(jdbc.queryForObject("SELECT to_tsvector('english', ?)::text", String.class,
                question)).keySet();
        if (terms.isEmpty()) {
            return List.of();
        }
        return rank(terms, chunks(ownerId, policyId), limit);
    }

    /**
     * The policy's exclusion clauses when the question asks whether something is covered: a
     * question about Botox never shares a word with "cosmetic procedures", yet that clause is the
     * answer.
     */
    public List<Document> exclusions(String question, Long ownerId, UUID policyId, int limit) {
        if (!COVERAGE_QUESTION.matcher(question).find()) {
            return List.of();
        }
        return chunks(ownerId, policyId).stream()
                .filter(c -> EXCLUSION.matcher(c.document().getText()).find())
                .limit(limit)
                .map(Chunk::document)
                .toList();
    }

    static List<Document> rank(Set<String> terms, List<Chunk> chunks, int limit) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        double averageLength = chunks.stream().mapToInt(Chunk::length).average().orElse(1);
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Chunk chunk : chunks) {
            for (String term : terms) {
                if (chunk.frequencies().containsKey(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }
        int n = chunks.size();
        record Scored(Document document, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            double score = 0;
            int matched = 0;
            for (String term : terms) {
                int tf = chunk.frequencies().getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                matched++;
                int df = documentFrequency.get(term);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                score += idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * chunk.length() / averageLength));
            }
            if (matched * 2 >= terms.size()) {
                scored.add(new Scored(chunk.document(), score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .map(Scored::document)
                .toList();
    }

    private List<Chunk> chunks(Long ownerId, UUID policyId) {
        return jdbc.query("""
                        SELECT id::text, content, metadata::text, to_tsvector('english', content)::text
                        FROM vector_store
                        WHERE metadata->>'%s' = ? AND metadata->>'%s' = ?
                        ORDER BY (metadata->>'%s')::int
                        """.formatted(OwnerScope.META_OWNER_ID, ProcessingService.META_DOCUMENT_ID,
                        ProcessingService.META_CHUNK_INDEX),
                (rs, row) -> {
                    Map<String, Object> metadata = JSON.readValue(rs.getString(3), new TypeReference<>() {
                    });
                    Map<String, Integer> frequencies = lexemes(rs.getString(4));
                    int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
                    Document document = Document.builder().id(rs.getString(1)).text(rs.getString(2))
                            .metadata(metadata).build();
                    return new Chunk(document, frequencies, Math.max(length, 1));
                },
                ownerId.toString(), policyId.toString());
    }

    /** Parses Postgres tsvector text ('crown':5 'major':3,8) into lexeme → count. */
    static Map<String, Integer> lexemes(String tsvector) {
        Map<String, Integer> counts = new HashMap<>();
        if (tsvector == null) {
            return counts;
        }
        Matcher m = LEXEME.matcher(tsvector);
        while (m.find()) {
            counts.put(m.group(1).replace("''", "'"), m.group(2).split(",").length);
        }
        return counts;
    }

    record Chunk(Document document, Map<String, Integer> frequencies, int length) {
    }
}
