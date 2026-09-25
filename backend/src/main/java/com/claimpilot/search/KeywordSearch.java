package com.claimpilot.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.claimpilot.document.OwnerScope;
import com.claimpilot.document.ProcessingService;

/**
 * Keyword search over one policy's chunks, next to the vector search. Embeddings miss exact
 * terms ("crown", "predetermination") when a chunk talks about several things at once; a word
 * match finds them. Postgres stems the words (crowns → crown) and the chunks are ranked with
 * BM25; words found in almost every question about a policy ("claim", "plan") are left out. A chunk must
 * hold at least half of the question's words: one shared word ("included") is not a match.
 * <p>
 * The stemmed words are stored with each chunk in a generated column with a GIN index, and chunks
 * are looked up by policy through an index, so a question reads only the chunks that share a word
 * with it, however long the policy.
 */
@Component
public class KeywordSearch {

    private static final Logger log = LoggerFactory.getLogger(KeywordSearch.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern LEXEME = Pattern.compile("'((?:[^'\\\\]|''|\\\\.)+)'(?::([0-9A-D,]+))?");
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** Wording of an exclusions clause (a Postgres regular expression, case-insensitive). */
    private static final String EXCLUSION =
            "exclu|not covered|does not cover|not eligible|not reimbursed|non couvert|ne couvre pas";
    /** An exclusions section itself, ranked before a single "is not covered" sentence elsewhere. */
    private static final String EXCLUSION_SECTION = "exclu|does not cover|ne couvre pas";
    /**
     * Stems found in almost every question about a policy. They say nothing about which clause is
     * meant, and a claims page that repeats them would outrank the clause with the rare word.
     */
    static final Set<String> DOMAIN_WORDS = Set.of("claim", "plan", "polici", "insur", "insuranc", "benefit",
            "member", "cover", "coverag", "pay", "paid", "get", "need", "much");
    private static final Pattern COVERAGE_QUESTION = Pattern.compile(
            "(?i)\\bcover|\\breimburs|\\bpay for\\b|\\beligible\\b|\\bclaim for\\b|rembours|couvert|保不保|报销|赔");

    private static final String OWNER = "metadata->>'" + OwnerScope.META_OWNER_ID + "'";
    private static final String DOCUMENT = "metadata->>'" + ProcessingService.META_DOCUMENT_ID + "'";
    private static final String CHUNK_INDEX = "(metadata->>'" + ProcessingService.META_CHUNK_INDEX + "')::int";

    private final JdbcTemplate jdbc;
    private final boolean available;

    /** Takes the vector store so that its table exists before the column and indexes are added. */
    public KeywordSearch(JdbcTemplate jdbc, VectorStore vectorStore) {
        this.jdbc = jdbc;
        this.available = prepare(jdbc);
    }

    /** Adds the stemmed-words column and the indexes once; later starts find them in place. */
    static boolean prepare(JdbcTemplate jdbc) {
        try {
            jdbc.execute("ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS words tsvector "
                    + "GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED");
            jdbc.execute("CREATE INDEX IF NOT EXISTS vector_store_words_idx ON vector_store USING GIN (words)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS vector_store_document_idx ON vector_store ((" + DOCUMENT + "))");
            return true;
        } catch (RuntimeException ex) {
            log.warn("Keyword search is off: the vector_store table could not be prepared ({})", ex.getMessage());
            return false;
        }
    }

    /** The best matching chunks for the question, best first. Empty when no word matches. */
    public List<Document> search(String question, Long ownerId, UUID policyId, int limit) {
        if (!available) {
            return List.of();
        }
        Set<String> terms = new java.util.HashSet<>(lexemes(jdbc.queryForObject(
                "SELECT to_tsvector('english', ?)::text", String.class, question)).keySet());
        terms.removeAll(DOMAIN_WORDS);
        if (terms.isEmpty()) {
            return List.of();
        }
        Map<String, Object> size = jdbc.queryForMap(
                "SELECT count(*) AS n, coalesce(avg(length(words)), 1) AS average FROM vector_store "
                        + "WHERE " + DOCUMENT + " = ? AND " + OWNER + " = ?",
                policyId.toString(), ownerId.toString());
        int n = ((Number) size.get("n")).intValue();
        double averageLength = ((Number) size.get("average")).doubleValue();
        // Only the chunks sharing a word with the question; the text-to-tsquery cast keeps the
        // stems exactly as they are.
        List<Chunk> candidates = jdbc.query(
                "SELECT id::text, content, metadata::text, words::text FROM vector_store "
                        + "WHERE " + DOCUMENT + " = ? AND " + OWNER + " = ? AND words @@ ?::tsquery",
                CHUNK, policyId.toString(), ownerId.toString(), anyOf(terms));
        return rank(terms, candidates, limit, n, averageLength);
    }

    /**
     * The policy's exclusion clauses when the question asks whether something is covered: a
     * question about Botox never shares a word with "cosmetic procedures", yet that clause is the
     * answer.
     */
    public List<Document> exclusions(String question, Long ownerId, UUID policyId, int limit) {
        if (!available || !COVERAGE_QUESTION.matcher(question).find()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT id::text, content, metadata::text, words::text FROM vector_store "
                        + "WHERE " + DOCUMENT + " = ? AND " + OWNER + " = ? AND content ~* ? "
                        + "ORDER BY (content ~* ?) DESC, " + CHUNK_INDEX + " LIMIT ?",
                CHUNK, policyId.toString(), ownerId.toString(), EXCLUSION, EXCLUSION_SECTION, limit)
                .stream().map(Chunk::document).toList();
    }

    /** Ranks chunks that are the whole collection (used by tests). */
    static List<Document> rank(Set<String> terms, List<Chunk> chunks, int limit) {
        double averageLength = chunks.stream().mapToInt(Chunk::length).average().orElse(1);
        return rank(terms, chunks, limit, chunks.size(), averageLength);
    }

    /**
     * BM25 over the candidates. Every chunk holding a query word is a candidate, so a word's
     * document frequency counted over the candidates is its frequency over the whole policy.
     */
    static List<Document> rank(Set<String> terms, List<Chunk> candidates, int limit, int n, double averageLength) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Chunk chunk : candidates) {
            for (String term : terms) {
                if (chunk.frequencies().containsKey(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }
        record Scored(Document document, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Chunk chunk : candidates) {
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
            if (matched > 0 && matched * 2 >= terms.size()) {
                scored.add(new Scored(chunk.document(), score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .map(Scored::document)
                .toList();
    }

    /** A tsquery matching any of the stems, each quoted so that no character is read as an operator. */
    static String anyOf(Set<String> terms) {
        return terms.stream()
                .map(t -> "'" + t.replace("\\", "\\\\").replace("'", "''") + "'")
                .collect(Collectors.joining(" | "));
    }

    private static final RowMapper<Chunk> CHUNK = (rs, row) -> {
        Map<String, Object> metadata = JSON.readValue(rs.getString(3), new TypeReference<>() {
        });
        Map<String, Integer> frequencies = lexemes(rs.getString(4));
        Document document = Document.builder().id(rs.getString(1)).text(rs.getString(2)).metadata(metadata).build();
        // Length in distinct stems (Postgres length(tsvector)), the same measure as the average.
        return new Chunk(document, frequencies, Math.max(frequencies.size(), 1));
    };

    /** Parses Postgres tsvector text ('crown':5 'major':3,8) into stem → count. */
    static Map<String, Integer> lexemes(String tsvector) {
        Map<String, Integer> counts = new HashMap<>();
        if (tsvector == null) {
            return counts;
        }
        Matcher m = LEXEME.matcher(tsvector);
        while (m.find()) {
            String stem = m.group(1).replace("''", "'").replace("\\\\", "\\");
            counts.put(stem, m.group(2) == null ? 1 : m.group(2).split(",").length);
        }
        return counts;
    }

    record Chunk(Document document, Map<String, Integer> frequencies, int length) {
    }
}
