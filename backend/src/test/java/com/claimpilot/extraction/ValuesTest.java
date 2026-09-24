package com.claimpilot.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ValuesTest {

    @Test
    void readsEnglishAndFrenchAmounts() {
        assertThat(Values.parseAmount("Total charged: $120.00")).contains(new BigDecimal("120.00"));
        assertThat(Values.parseAmount("Montant : 84,00 $")).contains(new BigDecimal("84.00"));
        assertThat(Values.parseAmount("$1,234.50")).contains(new BigDecimal("1234.50"));
        assertThat(Values.parseAmount("1 234,50 $")).contains(new BigDecimal("1234.50"));
        assertThat(Values.parseAmount("no amount")).isEmpty();
    }

    @Test
    void readsCommonDateFormats() {
        LocalDate march5 = LocalDate.of(2026, 3, 5);
        assertThat(Values.parseDate("Date of service: March 5, 2026")).contains(march5);
        assertThat(Values.parseDate("2026-03-05")).contains(march5);
        assertThat(Values.parseDate("le 5 mars 2026")).contains(march5);
        assertThat(Values.parseDate("1er mars 2026")).contains(LocalDate.of(2026, 3, 1));
        assertThat(Values.parseDate("5 Mar 2026")).contains(march5);
    }

    @Test
    void normalizesByType() {
        assertThat(Values.normalize(FactType.DATE, "March 5, 2026")).contains("2026-03-05");
        assertThat(Values.normalize(FactType.AMOUNT, "$84")).contains("84.00");
        assertThat(Values.normalize(FactType.TEXT, "  Marc   Gagnon ")).contains("Marc Gagnon");
        assertThat(Values.normalize(FactType.TEXT, "null")).isEmpty();
        assertThat(Values.normalize(FactType.DATE, "sometime in spring")).isEmpty();
    }

    @Test
    void checksThatTheQuoteContainsTheValue() {
        assertThat(Values.quoteSupports(FactType.TEXT, "CV-88213-02", "Group policy number: CV-88213-02")).isTrue();
        assertThat(Values.quoteSupports(FactType.TEXT, "CV-99999-99", "Group policy number: CV-88213-02")).isFalse();
        assertThat(Values.quoteSupports(FactType.AMOUNT, "84.00", "Paid by Harbourline Vie (direct billing): $84.00"))
                .isTrue();
        assertThat(Values.quoteSupports(FactType.DATE, "2026-03-05", "Date of service: March 5, 2026")).isTrue();
        assertThat(Values.quoteSupports(FactType.PHONE, "18005550199", "Member Services: 1-800-555-0199")).isTrue();
        assertThat(Values.quoteSupports(FactType.TEXT, "Atelier Boreal Design inc.",
                "Preneur (employeur) : Atelier Boréal Design inc.")).isTrue();
    }
}
