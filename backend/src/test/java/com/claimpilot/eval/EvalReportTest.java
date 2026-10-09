package com.claimpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The scoring helpers of the accuracy evaluation, checked in the normal build. */
class EvalReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void anExpectedPageCanBeOnePageOrSeveral() {
        assertThat(EvalScoring.pageMatches(JSON.readTree("10"), List.of(13, 10))).isTrue();
        assertThat(EvalScoring.pageMatches(JSON.readTree("10"), List.of(13))).isFalse();
        assertThat(EvalScoring.pageMatches(JSON.readTree("[10, 13]"), List.of(13))).isTrue();
        assertThat(EvalScoring.pageMatches(JSON.readTree("[10, 13]"), List.of(11))).isFalse();
        assertThat(EvalScoring.pageMatches(JSON.readTree("[10, 13]"), List.of())).isFalse();
    }

    @Test
    void mustContainAcceptsAlternatives() {
        assertThat(EvalScoring.contains("The limit is $2 million.", JSON.readTree("[\"2,000,000|2 million\"]")))
                .isTrue();
        assertThat(EvalScoring.contains("The limit is $1 million.", JSON.readTree("[\"2,000,000|2 million\"]")))
                .isFalse();
    }

    @Test
    void anAnswerFitsOnOneTableSafeLine() {
        assertThat(EvalScoring.excerpt("Yes.\n\nA | B [1]")).isEqualTo("Yes. A / B [1]");
        assertThat(EvalScoring.excerpt("x".repeat(500))).hasSize(401).endsWith("…");
        assertThat(EvalScoring.excerpt(null)).isEmpty();
    }
}
