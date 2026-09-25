package com.claimpilot.assistant;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.claimpilot.audit.AuditAction;
import com.claimpilot.audit.AuditService;
import com.claimpilot.chat.ChatAnswer;
import com.claimpilot.chat.ChatRequest;
import com.claimpilot.chat.ChatService;
import com.claimpilot.claim.ClaimDraftService;
import com.claimpilot.claim.ClaimDtos;
import com.claimpilot.claim.ClaimGuide;
import com.claimpilot.claim.ClaimGuideService;
import com.claimpilot.claim.CoordinationService;
import com.claimpilot.claim.SourceType;
import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentResponse;
import com.claimpilot.document.DocumentService;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.AppUser;
import com.claimpilot.user.Profile;
import com.claimpilot.user.ProfileRepository;

/**
 * One message in, the right modules out. A single model call turns the member's message into a
 * plan (answer a question, show the claim rules, fill the claim form, or several of these); code
 * validates the plan against the member's documents and runs the existing services in order. The
 * model never calls anything itself, so every step keeps its own checks: cited answers, guide items
 * tied to clauses, and form fields with their sources.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatClient chatClient;
    private final DocumentService documents;
    private final ProfileRepository profiles;
    private final ChatService chat;
    private final ClaimGuideService guides;
    private final ClaimDraftService drafts;
    private final AuditService audit;
    private final CoordinationService coordination;

    public AssistantService(ChatClient.Builder builder, DocumentService documents, ProfileRepository profiles,
                            ChatService chat, ClaimGuideService guides, ClaimDraftService drafts, AuditService audit,
                            CoordinationService coordination) {
        this.coordination = coordination;
        this.chatClient = builder.build();
        this.documents = documents;
        this.profiles = profiles;
        this.chat = chat;
        this.guides = guides;
        this.drafts = drafts;
        this.audit = audit;
    }

    public AssistantDtos.Reply handle(AppUser user, AssistantDtos.Request request) {
        long started = System.nanoTime();
        AssistantContext context = context(user);
        String reply = chatClient.prompt()
                .system(PlannerPrompt.SYSTEM)
                .user(PlannerPrompt.user(request.message(), context))
                .call()
                .content();
        AssistantPlan plan = AssistantPlan.from(reply, context, request);
        log.info("Assistant plan for user {}: {}", user.getId(), plan.actions());

        List<AssistantDtos.Step> steps = new ArrayList<>();
        if (plan.actions().isEmpty()) {
            steps.add(AssistantDtos.Step.message("What I can do", CAPABILITIES));
        } else if (plan.clarify() != null) {
            steps.add(AssistantDtos.Step.clarify(plan.clarify()));
        } else {
            run(user, plan, context, steps, request.policyId() != null);
        }
        audit.record(user.getId(), AuditAction.ASSISTANT_USED, null, null,
                plan.actions().isEmpty() ? "no action" : plan.actions().toString());
        return new AssistantDtos.Reply(summary(plan, context, steps), plan.actions(), steps,
                (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Coordination of benefits: a balance is claimed on the plan that pays second. If the plan would
     * claim on the one that pays first, the plans are swapped, or, when the member picked the plan
     * themselves, they are told which order the rules give.
     */
    private AssistantPlan checkOrder(AppUser user, AssistantPlan plan, boolean chosenByMember,
                                     List<AssistantDtos.Step> steps) {
        return coordination.conflictWith(user, plan.policyId(), plan.relationship(), null)
                .map(decision -> {
                    if (chosenByMember) {
                        steps.add(AssistantDtos.Step.note("Check which plan pays first", decision.explanation()));
                        return plan;
                    }
                    steps.add(AssistantDtos.Step.note("Claiming on the plan that pays second",
                            decision.explanation()));
                    return plan.withPlans(decision.secondPolicyId(), decision.firstPolicyId(),
                            decision.relationshipOnSecond());
                })
                .orElse(plan);
    }

    private void run(AppUser user, AssistantPlan plan, AssistantContext context, List<AssistantDtos.Step> steps,
                     boolean chosenByMember) {
        String policyName = name(context.policies(), plan);
        for (AssistantAction action : plan.actions()) {
            try {
                switch (action) {
                    case ASK -> {
                        ChatAnswer answer = chat.ask(new ChatRequest(plan.question(), plan.policyId(), null), user);
                        steps.add(AssistantDtos.Step.answer("From " + policyName, answer));
                    }
                    case GUIDE -> {
                        ClaimGuide guide = guides.guide(user, plan.policyId(), plan.claimType());
                        steps.add(AssistantDtos.Step.guide(plan.claimType().label() + ": what " + policyName
                                + " requires", guide));
                    }
                    case FILL -> {
                        AssistantPlan claim = checkOrder(user, plan, chosenByMember, steps);
                        ClaimDtos.Draft draft = drafts.create(user, new ClaimDtos.CreateDraft(claim.claimType(),
                                claim.policyId(), claim.otherPolicyId(), claim.receiptId(), claim.relationship(), null));
                        steps.add(AssistantDtos.Step.claim("Claim form, pre-filled", draft));
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Assistant step {} failed", action, ex);
                steps.add(AssistantDtos.Step.message("Could not " + describe(action), ex.getMessage()));
            }
        }
    }

    private AssistantContext context(AppUser user) {
        List<AssistantContext.Doc> policies = ready(documents.list(user, DocumentKind.POLICY));
        List<AssistantContext.Doc> receipts = ready(documents.list(user, DocumentKind.RECEIPT));
        String name = profiles.findById(user.getId()).map(Profile::getFullName).orElse(user.getDisplayName());
        return new AssistantContext(policies, receipts, name);
    }

    private static List<AssistantContext.Doc> ready(List<DocumentResponse> docs) {
        return docs.stream()
                .filter(d -> d.status() == DocumentStatus.READY)
                .map(d -> new AssistantContext.Doc(d.id(), d.fileName(), facts(d)))
                .toList();
    }

    private static Map<FactKey, String> facts(DocumentResponse doc) {
        Map<FactKey, String> facts = new EnumMap<>(FactKey.class);
        doc.facts().forEach(f -> facts.put(FactKey.valueOf(f.key()), f.value()));
        return facts;
    }

    private static String name(List<AssistantContext.Doc> policies, AssistantPlan plan) {
        return policies.stream().filter(p -> p.id().equals(plan.policyId())).findFirst()
                .map(p -> p.fact(FactKey.INSURER_NAME) == null ? p.fileName() : p.fact(FactKey.INSURER_NAME))
                .orElse("your policy");
    }

    /** Written by code from what actually happened, so it never claims a step that failed. */
    static String summary(AssistantPlan plan, AssistantContext context, List<AssistantDtos.Step> steps) {
        if (plan.actions().isEmpty()) {
            return "I can answer questions about your policies, show what a claim needs, and pre-fill claim forms.";
        }
        if (plan.clarify() != null) {
            return plan.clarify().question();
        }
        List<String> done = new ArrayList<>();
        for (AssistantDtos.Step step : steps) {
            switch (step.type()) {
                case "ANSWER" -> done.add("answered from " + name(context.policies(), plan));
                case "GUIDE" -> done.add("listed what the claim needs");
                case "CLAIM" -> {
                    long filled = step.draft().fields().stream()
                            .filter(f -> f.sourceType() != SourceType.MISSING).count();
                    done.add("pre-filled the claim form (" + filled + " of " + step.draft().fields().size()
                            + " fields; check each one before downloading)");
                }
                default -> {
                }
            }
        }
        long failed = steps.stream().filter(s -> s.type().equals("MESSAGE")).count();
        String text = done.isEmpty() ? "" : "I " + String.join(", ", done) + ".";
        if (failed > 0) {
            text += (text.isEmpty() ? "" : " ") + failed + (failed == 1 ? " step" : " steps") + " could not run; see below.";
        }
        return text;
    }

    private static String describe(AssistantAction action) {
        return switch (action) {
            case ASK -> "answer from the policy";
            case GUIDE -> "list what the claim needs";
            case FILL -> "pre-fill the claim form";
        };
    }

    static final String CAPABILITIES = "Ask me about your coverage (\"Is massage covered?\"), how to claim "
            + "(\"How do I claim dental on my husband's plan?\"), or tell me about a bill and I will pre-fill the "
            + "claim form (\"My physio cost $120 and my plan paid $84, claim the rest\").";
}
