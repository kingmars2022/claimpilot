package com.claimpilot.claim;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.Profile;

/**
 * Decides where each data item on a second-plan claim comes from. Pure code, no AI: the model has
 * already read the documents; this class only picks, combines and labels the values.
 *
 * <pre>
 * plan member, policy, certificate, sponsor  from the plan being claimed on (usually the spouse's)
 * other insurer, policy, certificate         from the plan that paid first (usually the member's own)
 * provider, date, service, amounts           from the receipt
 * patient name, date of birth, address       from the profile
 * relationship                               from the claim setup
 * amount claimed                             calculated: charged minus paid by the other plan
 * </pre>
 */
public final class ClaimValueAssembler {

    /** One source document with the facts read from it. */
    public record Source(UUID documentId, String fileName, Map<FactKey, DocumentFact> facts) {

        static Source none() {
            return new Source(null, null, Map.of());
        }
    }

    private ClaimValueAssembler() {
    }

    public static Map<DataKey, DraftValue> assemble(Source policy, Source otherPolicy, Source receipt,
                                                    Profile profile, Relationship relationship) {
        Map<DataKey, DraftValue> values = new EnumMap<>(DataKey.class);
        values.put(DataKey.MEMBER_NAME, fromFact(policy, FactKey.PLAN_MEMBER_NAME, SourceType.POLICY));
        values.put(DataKey.POLICY_NUMBER, fromFact(policy, FactKey.POLICY_NUMBER, SourceType.POLICY));
        values.put(DataKey.CERTIFICATE_NUMBER, fromFact(policy, FactKey.CERTIFICATE_NUMBER, SourceType.POLICY));
        values.put(DataKey.PLAN_SPONSOR, fromFact(policy, FactKey.PLAN_SPONSOR, SourceType.POLICY));

        values.put(DataKey.OTHER_INSURER, fromFact(otherPolicy, FactKey.INSURER_NAME, SourceType.OTHER_POLICY));
        values.put(DataKey.OTHER_POLICY_NUMBER, fromFact(otherPolicy, FactKey.POLICY_NUMBER, SourceType.OTHER_POLICY));
        values.put(DataKey.OTHER_CERTIFICATE_NUMBER,
                fromFact(otherPolicy, FactKey.CERTIFICATE_NUMBER, SourceType.OTHER_POLICY));

        values.put(DataKey.PROVIDER_NAME, fromFact(receipt, FactKey.PROVIDER_NAME, SourceType.RECEIPT));
        values.put(DataKey.SERVICE_DATE, fromFact(receipt, FactKey.SERVICE_DATE, SourceType.RECEIPT));
        values.put(DataKey.SERVICE_TYPE, fromFact(receipt, FactKey.SERVICE_TYPE, SourceType.RECEIPT));
        values.put(DataKey.AMOUNT_CHARGED, fromFact(receipt, FactKey.AMOUNT_CHARGED, SourceType.RECEIPT));
        values.put(DataKey.AMOUNT_PAID_BY_OTHER_PLAN,
                fromFact(receipt, FactKey.AMOUNT_PAID_BY_OTHER_PLAN, SourceType.RECEIPT));

        values.put(DataKey.PATIENT_NAME, profileValue(profile == null ? null : profile.getFullName(), "full name"));
        if (values.get(DataKey.PATIENT_NAME).sourceType() == SourceType.MISSING) {
            values.put(DataKey.PATIENT_NAME, fromFact(receipt, FactKey.PATIENT_NAME, SourceType.RECEIPT));
        }
        values.put(DataKey.PATIENT_DOB, profileValue(
                profile == null || profile.getDateOfBirth() == null ? null : profile.getDateOfBirth().toString(),
                "date of birth"));
        values.put(DataKey.PATIENT_ADDRESS, profileValue(profile == null ? null : profile.address(), "address"));
        values.put(DataKey.PATIENT_RELATIONSHIP, new DraftValue(relationship.label(), SourceType.CLAIM_SETUP, null,
                "Chosen when you started this claim", null, null, true));

        values.put(DataKey.AMOUNT_CLAIMED, amountClaimed(values.get(DataKey.AMOUNT_CHARGED),
                values.get(DataKey.AMOUNT_PAID_BY_OTHER_PLAN)));
        return values;
    }

    /** Charged minus what the first plan paid; missing when either amount is missing. */
    static DraftValue amountClaimed(DraftValue charged, DraftValue paid) {
        if (charged.value() == null) {
            return DraftValue.missing();
        }
        BigDecimal total = new BigDecimal(charged.value());
        BigDecimal paidFirst = paid.value() == null ? BigDecimal.ZERO : new BigDecimal(paid.value());
        BigDecimal remaining = total.subtract(paidFirst).max(BigDecimal.ZERO);
        String how = paid.value() == null
                ? "Amount charged (nothing paid by another plan was found on the receipt)"
                : "Amount charged " + charged.value() + " minus paid by other plan " + paid.value();
        return new DraftValue(remaining.setScale(2).toPlainString(), SourceType.CALCULATED, null, how, null, null,
                charged.verified() && (paid.value() == null || paid.verified()));
    }

    private static DraftValue fromFact(Source source, FactKey key, SourceType type) {
        DocumentFact fact = source.facts().get(key);
        if (fact == null) {
            return DraftValue.missing();
        }
        String label = source.fileName() + (fact.getPage() == null ? "" : ", page " + fact.getPage());
        return new DraftValue(fact.getValue(), type, source.documentId(), label, fact.getPage(), fact.getQuote(),
                fact.isVerified());
    }

    private static DraftValue profileValue(String value, String what) {
        if (value == null || value.isBlank()) {
            return DraftValue.missing();
        }
        return new DraftValue(value, SourceType.PROFILE, null, "Your profile (" + what + ")", null, null, true);
    }
}
