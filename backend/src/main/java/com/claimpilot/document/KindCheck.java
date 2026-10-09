package com.claimpilot.document;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.claimpilot.extraction.ExtractedFact;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.extraction.PageText;

/**
 * Catches a document uploaded in the wrong place: a receipt among the policies, or a policy among
 * the receipts. A receipt read as a policy would otherwise take part in "which plan pays first".
 * Both signals must agree: the facts the document should have are missing (or could not be found in
 * its text), and its text uses the other kind's wording.
 */
final class KindCheck {

    private static final Pattern RECEIPT_WORDING = Pattern.compile(
            "(?i)\\breceipt\\b|\\bre[çc]u\\b|paid by patient|pay[ée] par le patient|total charged|amount charged|"
                    + "montant factur[ée]|date of service|date du service");
    private static final Pattern POLICY_WORDING = Pattern.compile(
            "(?i)policy number|group policy|num[ée]ro de police|certificate number|num[ée]ro de certificat|"
                    + "plan member|adh[ée]rent|benefits booklet|coverage|garanties");

    private KindCheck() {
    }

    static Optional<String> warning(DocumentKind kind, List<PageText> pages, List<ExtractedFact> facts) {
        Set<FactKey> found = facts.stream().filter(f -> f.verified() && f.value() != null && !f.value().isBlank())
                .map(ExtractedFact::key).collect(Collectors.toSet());
        String text = pages.stream().map(PageText::text).collect(Collectors.joining("\n")).toLowerCase(Locale.ROOT);
        if (kind == DocumentKind.POLICY && !found.contains(FactKey.POLICY_NUMBER)
                && !found.contains(FactKey.CERTIFICATE_NUMBER) && RECEIPT_WORDING.matcher(text).find()) {
            return Optional.of("This looks like a receipt, not a policy: no policy or certificate number was found. "
                    + "Delete it and add it under Receipts. It is not used to decide which plan pays first.");
        }
        if (kind == DocumentKind.RECEIPT && !found.contains(FactKey.AMOUNT_CHARGED)
                && !found.contains(FactKey.SERVICE_DATE) && POLICY_WORDING.matcher(text).find()) {
            return Optional.of("This looks like a policy, not a receipt: no amount or date of service was found. "
                    + "Delete it and add it under Policies.");
        }
        return Optional.empty();
    }
}
