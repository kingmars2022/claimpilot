package com.claimpilot.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.DocumentFactRepository;
import com.claimpilot.extraction.FactKey;

/**
 * Builds the call kit from facts already extracted at upload time. No model call is needed:
 * the script is a template, so it is instant and always well formed.
 */
@Component
public class CallKitBuilder {

    private final DocumentFactRepository facts;

    public CallKitBuilder(DocumentFactRepository facts) {
        this.facts = facts;
    }

    /**
     * @param englishQuestion the question in English, to be read to the agent
     */
    public CallKit build(UUID policyId, String englishQuestion) {
        Map<FactKey, DocumentFact> byKey = facts.findByDocumentId(policyId).stream()
                .collect(Collectors.toMap(DocumentFact::getKey, Function.identity(), (a, b) -> a));
        return build(byKey, englishQuestion);
    }

    static CallKit build(Map<FactKey, DocumentFact> byKey, String englishQuestion) {
        List<CallKit.Detail> details = new ArrayList<>();
        addDetail(details, byKey, FactKey.POLICY_NUMBER, "Group policy number");
        addDetail(details, byKey, FactKey.CERTIFICATE_NUMBER, "Certificate / member ID");
        addDetail(details, byKey, FactKey.PLAN_MEMBER_NAME, "Plan member");
        addDetail(details, byKey, FactKey.PLAN_SPONSOR, "Employer (plan sponsor)");

        String insurer = value(byKey, FactKey.INSURER_NAME);
        return new CallKit(insurer, detail(byKey, FactKey.INSURER_PHONE, "Phone"),
                detail(byKey, FactKey.INSURER_HOURS, "Hours"), details, script(byKey, englishQuestion));
    }

    static String script(Map<FactKey, DocumentFact> byKey, String englishQuestion) {
        StringBuilder sb = new StringBuilder("Hello, I'm calling with a question about my group benefits");
        String member = value(byKey, FactKey.PLAN_MEMBER_NAME);
        if (member != null) {
            sb.append(". The plan member is ").append(member);
        }
        String policy = value(byKey, FactKey.POLICY_NUMBER);
        String certificate = value(byKey, FactKey.CERTIFICATE_NUMBER);
        if (policy != null) {
            sb.append(", group policy number ").append(policy);
        }
        if (certificate != null) {
            sb.append(", certificate number ").append(certificate);
        }
        sb.append(".\n\nMy question is: ").append(englishQuestion.strip());
        if (!englishQuestion.strip().endsWith("?")) {
            sb.append('?');
        }
        sb.append("\n\nCould you also tell me which section of my policy covers this, "
                + "and send me the answer in writing if possible? Thank you.");
        return sb.toString();
    }

    private static void addDetail(List<CallKit.Detail> details, Map<FactKey, DocumentFact> byKey, FactKey key,
                                  String label) {
        CallKit.Detail detail = detail(byKey, key, label);
        if (detail != null) {
            details.add(detail);
        }
    }

    private static CallKit.Detail detail(Map<FactKey, DocumentFact> byKey, FactKey key, String label) {
        DocumentFact fact = byKey.get(key);
        return fact == null ? null : new CallKit.Detail(label, fact.getValue(), fact.getPage(), fact.isVerified());
    }

    private static String value(Map<FactKey, DocumentFact> byKey, FactKey key) {
        DocumentFact fact = byKey.get(key);
        return fact == null ? null : fact.getValue();
    }
}
