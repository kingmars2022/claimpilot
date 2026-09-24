package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ClaimGuideServiceTest {

    private static final List<Document> CLAUSES = List.of(
            new Document("Claims must be received within 12 months of the date the expense was incurred.",
                    Map.of("page", 3)),
            new Document("Massage therapist: a physician's referral is required.", Map.of("page", 2)));

    @Test
    void keepsOnlyItemsThatCiteARealClause() {
        String reply = """
                {"deadlines": [{"text": "Submit within 12 months of the service date", "source": 1},
                               {"text": "Submit within 30 days", "source": 9},
                               {"text": "No source given"}],
                 "documents": [],
                 "submission": [],
                 "coverage": [],
                 "preApproval": {"required": "YES", "text": "Get a referral for massage therapy", "source": "[2]"}}
                """;

        ClaimGuide guide = ClaimGuideService.parse(reply, UUID.randomUUID(), ClaimType.SECONDARY_PARAMEDICAL, CLAUSES);

        assertThat(guide.found()).isTrue();
        assertThat(guide.deadlines()).singleElement().satisfies(item -> {
            assertThat(item.text()).isEqualTo("Submit within 12 months of the service date");
            assertThat(item.page()).isEqualTo(3);
            assertThat(item.clause()).startsWith("Claims must be received");
        });
        assertThat(guide.preApproval().required()).isEqualTo(ClaimGuide.Requirement.YES);
        assertThat(guide.preApproval().basis().page()).isEqualTo(2);
    }

    @Test
    void preApprovalWithoutClauseIsUnknown() {
        ClaimGuide guide = ClaimGuideService.parse("{\"preApproval\": {\"required\": \"NO\", \"text\": \"None\"}}",
                UUID.randomUUID(), ClaimType.SECONDARY_DENTAL, CLAUSES);

        assertThat(guide.preApproval().required()).isEqualTo(ClaimGuide.Requirement.UNKNOWN);
        assertThat(guide.found()).isFalse();
    }
}
