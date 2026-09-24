package com.companybrain.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MarkdownSectionsTest {

    @Test
    void splitsAtHeadingsAndNamesSections() {
        List<Document> sections = MarkdownSections.split("""
                # Employee Handbook

                Fictional company.

                ## Vacation
                You get 15 days.

                ## Sick days
                You get 7 days.
                """);

        assertThat(sections).extracting(d -> d.getMetadata().get(MarkdownSections.META_SECTION))
                .containsExactly("Employee Handbook", "Vacation", "Sick days");
        assertThat(sections.get(1).getText()).isEqualTo("## Vacation\nYou get 15 days.");
    }

    @Test
    void nestedHeadingsBuildAPath() {
        List<Document> sections = MarkdownSections.split("""
                # Policy
                ## Travel
                Book economy.
                ### Mileage
                $0.68 per km.
                ## Approval
                Ask your manager.
                """);

        assertThat(sections).extracting(d -> d.getMetadata().get(MarkdownSections.META_SECTION))
                .containsExactly("Travel", "Travel › Mileage", "Approval");
    }

    @Test
    void skipsHeadingsWithoutBodyText() {
        List<Document> sections = MarkdownSections.split("# Title\n## Empty\n## Filled\nText.");

        assertThat(sections).singleElement()
                .extracting(d -> d.getMetadata().get(MarkdownSections.META_SECTION))
                .isEqualTo("Filled");
    }

    @Test
    void ignoresHashLinesInsideCodeFences() {
        List<Document> sections = MarkdownSections.split("""
                ## Setup
                ```bash
                # not a heading
                ```
                """);

        assertThat(sections).singleElement()
                .extracting(d -> d.getMetadata().get(MarkdownSections.META_SECTION))
                .isEqualTo("Setup");
    }

    @Test
    void plainTextBecomesOneUnnamedSection() {
        List<Document> sections = MarkdownSections.split("Just some notes.\nNo headings here.");

        assertThat(sections).singleElement().satisfies(d -> {
            assertThat(d.getMetadata()).doesNotContainKey(MarkdownSections.META_SECTION);
            assertThat(d.getText()).isEqualTo("Just some notes.\nNo headings here.");
        });
    }

    @Test
    void blankInputGivesNoSections() {
        assertThat(MarkdownSections.split("  \n ")).isEmpty();
        assertThat(MarkdownSections.split(null)).isEmpty();
    }
}
