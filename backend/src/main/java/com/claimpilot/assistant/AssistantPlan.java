package com.claimpilot.assistant;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import com.claimpilot.claim.ClaimType;
import com.claimpilot.claim.Relationship;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.extraction.JsonReply;

/**
 * The model proposes, the code decides. The model's plan is parsed and then checked against what
 * the member actually has: unknown actions and out-of-range choices are dropped, a claim is always
 * preceded by its guide, and anything still ambiguous becomes a question back to the member.
 *
 * @param clarify set when the plan cannot run without the member's answer
 */
public record AssistantPlan(List<AssistantAction> actions, String question, UUID policyId, UUID otherPolicyId,
                            UUID receiptId, ClaimType claimType, Relationship relationship,
                            AssistantDtos.Clarify clarify) {

    static AssistantPlan from(String reply, AssistantContext context, AssistantDtos.Request request) {
        JsonNode root = JsonReply.parse(reply);

        Set<AssistantAction> wanted = EnumSet.noneOf(AssistantAction.class);
        JsonNode steps = root.get("steps");
        if (steps != null && steps.isArray()) {
            for (JsonNode step : steps) {
                String name = step.isString() ? step.asString() : JsonReply.text(step, "action");
                AssistantAction action = parseAction(name);
                if (action != null) {
                    wanted.add(action);
                }
            }
        }
        if (wanted.contains(AssistantAction.FILL)) {
            wanted.add(AssistantAction.GUIDE);  // the member sees the rules before the form
        }
        List<AssistantAction> actions = new ArrayList<>(wanted);  // EnumSet keeps ASK, GUIDE, FILL order

        String question = JsonReply.text(root, "question");
        if (question == null) {
            question = request.message();
        }

        AssistantContext.Doc policy = request.policyId() != null
                ? byId(context.policies(), request.policyId())
                : pick(context.policies(), root, "policy");
        if (policy == null && context.policies().size() == 1) {
            policy = context.policies().getFirst();
        }

        AssistantContext.Doc other = pick(context.policies(), root, "otherPolicy");
        if (other != null && policy != null && other.id().equals(policy.id())) {
            other = null;
        }
        if (other == null && policy != null) {
            other = paidFirst(context, policy);
        }

        AssistantContext.Doc receipt = pick(context.receipts(), root, "receipt");
        if (receipt == null && !context.receipts().isEmpty()) {
            receipt = context.receipts().getFirst();  // the most recent
        }

        ClaimType claimType = request.claimType() != null ? request.claimType()
                : parseClaimType(JsonReply.text(root, "claimType"));
        if (claimType == null) {
            claimType = inferClaimType(request.message() + " "
                    + (receipt == null ? "" : String.valueOf(receipt.fact(FactKey.SERVICE_TYPE))));
        }

        Relationship relationship = parseRelationship(JsonReply.text(root, "relationship"));
        if (relationship == null && policy != null) {
            relationship = inferRelationship(policy, context.profileName());
        }

        AssistantDtos.Clarify clarify = null;
        if (!actions.isEmpty() && context.policies().isEmpty()) {
            clarify = new AssistantDtos.Clarify(
                    "Add your policy under My documents first, then ask again.", null, List.of());
        } else if (!actions.isEmpty() && policy == null) {
            clarify = new AssistantDtos.Clarify(
                    actions.contains(AssistantAction.FILL) ? "Which plan do you want to claim on?"
                            : "Which policy is this about?",
                    "policyId",
                    context.policies().stream()
                            .map(p -> new AssistantDtos.Option(p.label(), p.id().toString())).toList());
        } else if ((actions.contains(AssistantAction.GUIDE) || actions.contains(AssistantAction.FILL))
                && claimType == null) {
            clarify = new AssistantDtos.Clarify("What kind of care is the claim for?", "claimType",
                    java.util.Arrays.stream(ClaimType.values())
                            .map(t -> new AssistantDtos.Option(t.label(), t.name())).toList());
        }

        return new AssistantPlan(List.copyOf(actions), question, policy == null ? null : policy.id(),
                other == null ? null : other.id(), receipt == null ? null : receipt.id(), claimType,
                relationship == null ? Relationship.SELF : relationship, clarify);
    }

    /**
     * Without a choice from the model: the member's own plan (the one naming them as plan member)
     * is the one that paid first; with just two policies, it is simply the other one.
     */
    private static AssistantContext.Doc paidFirst(AssistantContext context, AssistantContext.Doc claimedOn) {
        List<AssistantContext.Doc> others = context.policies().stream()
                .filter(p -> !p.id().equals(claimedOn.id())).toList();
        if (others.size() == 1) {
            return others.getFirst();
        }
        return others.stream()
                .filter(p -> inferRelationship(p, context.profileName()) == Relationship.SELF)
                .findFirst().orElse(null);
    }

    /** The model refers to documents by their number in the list it was shown (1-based). */
    private static AssistantContext.Doc pick(List<AssistantContext.Doc> docs, JsonNode root, String field) {
        String value = JsonReply.text(root, field);
        if (value == null) {
            return null;
        }
        try {
            int index = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            return index >= 1 && index <= docs.size() ? docs.get(index - 1) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static AssistantContext.Doc byId(List<AssistantContext.Doc> docs, UUID id) {
        return docs.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    private static AssistantAction parseAction(String name) {
        if (name == null) {
            return null;
        }
        try {
            return AssistantAction.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ClaimType parseClaimType(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ClaimType.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Relationship parseRelationship(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Relationship.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Keywords in English, French and Chinese, checked against the message and the receipt. */
    static ClaimType inferClaimType(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.matches("(?s).*(physio|massage|massoth|chiro|kin[eé]si|ost[eé]o|acupunct|psycholog|理疗|按摩|针灸).*")) {
            return ClaimType.SECONDARY_PARAMEDICAL;
        }
        if (t.matches("(?s).*(dent|teeth|tooth|cleaning|牙).*")) {
            return ClaimType.SECONDARY_DENTAL;
        }
        if (t.matches("(?s).*(drug|pharma|prescription|m[eé]dicament|ordonnance|药).*")) {
            return ClaimType.SECONDARY_DRUGS;
        }
        if (t.matches("(?s).*(glasses|lens|eye exam|optom|lunettes|verres|眼镜|视力).*")) {
            return ClaimType.SECONDARY_VISION;
        }
        return null;
    }

    /** The member's own plan names them as plan member; otherwise the claim is made as a spouse. */
    static Relationship inferRelationship(AssistantContext.Doc policy, String profileName) {
        String member = policy.fact(FactKey.PLAN_MEMBER_NAME);
        if (member == null || profileName == null) {
            return null;
        }
        return normalize(member).equals(normalize(profileName)) ? Relationship.SELF : Relationship.SPOUSE;
    }

    private static String normalize(String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\p{L}]", "").toLowerCase(Locale.ROOT);
    }
}
