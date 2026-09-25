package com.claimpilot.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.claimpilot.config.AppProperties;
import com.claimpilot.document.OwnerScope;

/**
 * Finds a policy's clauses with two searches side by side, vector search (meaning) and keyword
 * search (exact terms), merged by rank. Always limited to one user's policy.
 */
@Service
public class HybridSearch {

    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final KeywordSearch keywords;
    private final double similarityThreshold;

    public HybridSearch(VectorStore vectorStore, KeywordSearch keywords, AppProperties properties) {
        this.vectorStore = vectorStore;
        this.keywords = keywords;
        this.similarityThreshold = properties.retrieval().similarityThreshold();
    }

    /**
     * Runs both searches for every query and merges all the lists.
     *
     * @param perQuery how many chunks each search returns for each query
     * @param limit    how many chunks to keep in total
     */
    public List<Document> search(List<String> queries, Long ownerId, UUID policyId, int perQuery, int limit) {
        List<List<Document>> lists = new ArrayList<>();
        for (String query : queries) {
            lists.add(vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(perQuery)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(OwnerScope.policy(ownerId, policyId))
                    .build()));
            lists.add(keywords.search(query, ownerId, policyId, perQuery));
        }
        return fuse(lists, limit);
    }

    /** The policy's exclusion clauses for a coverage question; see {@link KeywordSearch#exclusions}. */
    public List<Document> exclusions(String question, Long ownerId, UUID policyId, int limit) {
        return keywords.exclusions(question, ownerId, policyId, limit);
    }

    /**
     * Reciprocal rank fusion: each chunk scores 1/(60 + rank) in every list it appears in, so a
     * chunk found by both searches comes first. Keeps each chunk once, with its best similarity
     * score for display, at most {@code limit}.
     */
    static List<Document> fuse(List<List<Document>> lists, int limit) {
        Map<String, Double> fusedScore = new HashMap<>();
        Map<String, Document> byId = new LinkedHashMap<>();
        for (List<Document> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                Document doc = list.get(rank);
                fusedScore.merge(doc.getId(), 1.0 / (RRF_K + rank + 1), Double::sum);
                byId.merge(doc.getId(), doc, (a, b) -> score(b) > score(a) ? b : a);
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingDouble((Document d) -> fusedScore.get(d.getId())).reversed())
                .limit(limit)
                .toList();
    }

    private static double score(Document doc) {
        return doc.getScore() == null ? 0 : doc.getScore();
    }
}
