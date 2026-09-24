package com.claimpilot.assistant;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.claimpilot.chat.ChatAnswer;
import com.claimpilot.claim.ClaimDtos;
import com.claimpilot.claim.ClaimGuide;
import com.claimpilot.claim.ClaimType;
import com.claimpilot.claim.Relationship;

/** Request and response bodies for the assistant. */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /**
     * @param message   what the member wrote, in any language
     * @param policyId  set when the member answered "which policy?"; overrides the plan
     * @param claimType set when the member answered "which kind of care?"; overrides the plan
     * @param relationship set when the member answered "who received the care?"; overrides the plan
     */
    public record Request(@NotBlank @Size(max = 2000) String message, UUID policyId, ClaimType claimType,
                          Relationship relationship) {
    }

    public record Option(String label, String value) {
    }

    /** A question back to the member, with the choices that would unblock the plan. */
    public record Clarify(String question, String field, List<Option> options) {
    }

    /** One executed step; exactly one of the payload fields is set, matching {@code type}. */
    public record Step(String type, String title, ChatAnswer answer, ClaimGuide guide, ClaimDtos.Draft draft,
                       Clarify clarify, String text) {

        static Step answer(String title, ChatAnswer answer) {
            return new Step("ANSWER", title, answer, null, null, null, null);
        }

        static Step guide(String title, ClaimGuide guide) {
            return new Step("GUIDE", title, null, guide, null, null, null);
        }

        static Step claim(String title, ClaimDtos.Draft draft) {
            return new Step("CLAIM", title, null, null, draft, null, null);
        }

        static Step clarify(Clarify clarify) {
            return new Step("CLARIFY", clarify.question(), null, null, null, clarify, null);
        }

        static Step message(String title, String text) {
            return new Step("MESSAGE", title, null, null, null, null, text);
        }
    }

    /**
     * @param summary what was done, in one or two sentences
     * @param actions the steps the plan called for, after validation
     */
    public record Reply(String summary, List<AssistantAction> actions, List<Step> steps, long latencyMs) {
    }
}
