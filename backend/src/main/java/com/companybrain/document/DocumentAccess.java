package com.companybrain.document;

import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.companybrain.user.AppUser;
import com.companybrain.user.Department;

/**
 * Permission-aware retrieval. Every chunk carries an "access" list in its vector metadata:
 * ["ALL"] for company-wide documents, or department codes such as ["HR", "FINANCE"].
 * Searches add a filter on that list, so chunks a user may not see are never retrieved and
 * never reach the model.
 */
@Component
public class DocumentAccess {

    public static final String META_ACCESS = "access";
    public static final String EVERYONE = "ALL";

    // Spring AI's default table; its metadata column is json, hence the casts.
    private static final String UPDATE_ACCESS_SQL = """
            UPDATE vector_store
            SET metadata = jsonb_set(metadata::jsonb, '{access}', ?::jsonb)::json
            WHERE metadata::jsonb ->> 'documentId' = ?
            """;

    private final JdbcTemplate jdbc;

    public DocumentAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The access list stored on each chunk of this document. */
    public static List<String> accessList(KnowledgeDocument doc) {
        if (doc.getDepartments().isEmpty()) {
            return List.of(EVERYONE);
        }
        return doc.getDepartments().stream().map(Department::getCode).sorted().toList();
    }

    /**
     * Search filter for a user: company-wide chunks plus their own department's.
     * Admins get no filter and search everything.
     */
    public static Filter.Expression filterFor(AppUser user) {
        if (user.isAdmin()) {
            return null;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        if (user.getDepartment() == null) {
            return b.in(META_ACCESS, EVERYONE).build();
        }
        return b.in(META_ACCESS, EVERYONE, user.getDepartment().getCode()).build();
    }

    /**
     * Rewrites the access list on the document's existing chunks in place. Changing who can see
     * a document is a metadata update in SQL, not a re-embedding of the whole file.
     */
    public int applyToVectors(KnowledgeDocument doc) {
        return jdbc.update(UPDATE_ACCESS_SQL, toJsonArray(accessList(doc)), doc.getId().toString());
    }

    private static String toJsonArray(List<String> values) {
        // Department codes are validated to [A-Za-z0-9_], so no escaping is needed.
        return values.stream().map(v -> "\"" + v + "\"").reduce((a, b) -> a + "," + b)
                .map(joined -> "[" + joined + "]").orElse("[]");
    }
}
