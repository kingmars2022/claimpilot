package com.claimpilot.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.claimpilot.cache.CachedModel;
import com.claimpilot.config.AppProperties;
import com.claimpilot.conversation.ChatMessage;
import com.claimpilot.conversation.Conversation;
import com.claimpilot.conversation.ConversationService;
import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentService;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.document.ProcessingService;
import com.claimpilot.document.UploadedDocument;
import com.claimpilot.search.HybridSearch;
import com.claimpilot.user.AppUser;

/**
 * Module 1, "ask your policy". Finds the clauses of one of the user's policies, lets the model
 * answer from those clauses only, and classifies the result: answered, unclear, or not in the
 * policy. When the policy is unclear or silent, the user gets a call kit for the insurer.
 */
@Service
public class ChatService {

    private static final int SNIPPET_LENGTH = 320;
    private static final int UNCLEAR_CLAUSES = 3;
    private static final int EXCLUSION_CLAUSES = 2;

    private final CachedModel model;
    private final HybridSearch search;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversations;
    private final DocumentService documents;
    private final CallKitBuilder callKits;
    private final AppProperties.Retrieval retrieval;
    private final int historyTurns;

    public ChatService(CachedModel model, HybridSearch search, PromptBuilder promptBuilder,
                       ConversationService conversations, DocumentService documents, CallKitBuilder callKits,
                       AppProperties properties) {
        this.model = model;
        this.search = search;
        this.promptBuilder = promptBuilder;
        this.conversations = conversations;
        this.documents = documents;
        this.callKits = callKits;
        this.retrieval = properties.retrieval();
        this.historyTurns = properties.conversation().historyTurns();
    }

    public ChatAnswer ask(ChatRequest request, AppUser user) {
        long start = System.nanoTime();
        String question = request.question().strip();
        Conversation conversation = request.conversationId() == null || request.conversationId().isBlank()
                ? Conversation.start(user.getUsername(), request.policyId(), question)
                : conversations.getOwned(request.conversationId(), user.getUsername());
        UUID policyId = conversation.getPolicyId();
        UploadedDocument policy = documents.find(user, DocumentKind.POLICY, policyId);
        if (policy.getStatus() != DocumentStatus.READY) {
            throw new IllegalArgumentException("This policy is still being processed. Try again in a moment.");
        }

        AnswerLanguage language = AnswerLanguage.detect(question);
        List<ChatMessage> history = conversation.recentMessages(historyTurns);
        List<Document> sources = retrieve(question, history, user, policyId);

        AnswerStatus status;
        String answer;
        List<Citation> citations;
        if (sources.isEmpty()) {
            // Nothing relevant in the policy: say so without a model call.
            status = AnswerStatus.NOT_IN_POLICY;
            answer = language.notInPolicyMessage();
            citations = List.of();
        } else {
            PromptBuilder.ParsedReply reply = PromptBuilder.parse(model.complete(user.getId(),
                    promptBuilder.systemPrompt(language),
                    promptBuilder.userPrompt(question, sources, history, language)));
            citations = citationsUsed(reply.text(), sources);
            status = reply.status().orElse(citations.isEmpty() ? AnswerStatus.UNCLEAR : AnswerStatus.ANSWERED);
            answer = reply.text();
            if (status == AnswerStatus.ANSWERED && citations.isEmpty()) {
                // An answer that cites nothing is not grounded: treat it as unclear and show the clauses.
                status = AnswerStatus.UNCLEAR;
            }
            boolean discretionary = false;
            if (status == AnswerStatus.ANSWERED && DiscretionaryWording.decides(answer, citedTexts(answer, sources))) {
                // The clause leaves it to the insurer ("may be considered"): not a yes.
                status = AnswerStatus.UNCLEAR;
                discretionary = true;
            }
            if (status == AnswerStatus.UNCLEAR && !discretionary) {
                // Two cases the model calls unclear that code can settle exactly.
                var threshold = ThresholdRule.find(question, sources);
                if (threshold.isPresent()) {
                    status = AnswerStatus.ANSWERED;
                    answer = threshold.get().answer(language);
                    citations = citationsUsed(answer, sources);
                } else if (UnmentionedItem.applies(question, language, sources)) {
                    status = AnswerStatus.NOT_IN_POLICY;
                }
            }
            if (status == AnswerStatus.NOT_IN_POLICY || answer.isBlank()) {
                status = AnswerStatus.NOT_IN_POLICY;
                answer = language.notInPolicyMessage();
                citations = List.of();
            } else if (status == AnswerStatus.UNCLEAR && citations.isEmpty()) {
                citations = asCitations(sources.subList(0, Math.min(UNCLEAR_CLAUSES, sources.size())));
            }
        }

        CallKit callKit = status == AnswerStatus.ANSWERED ? null
                : callKits.build(policyId, englishQuestion(user, question, language));
        conversation.append(ChatMessage.question(question),
                ChatMessage.answer(answer, status, citations, callKit));
        Conversation saved = conversations.save(conversation);
        return new ChatAnswer(answer, status, language.code(), citations, callKit, elapsedMs(start), saved.getId(),
                policyId);
    }

    /** The call script is read to an English-speaking agent, so a French or Chinese question is translated. */
    private String englishQuestion(AppUser user, String question, AnswerLanguage language) {
        if (language == AnswerLanguage.ENGLISH) {
            return question;
        }
        try {
            String translated = PromptBuilder.clean(model.complete(user.getId(), null,
                    promptBuilder.translatePrompt(question)));
            return translated.isBlank() ? question : translated.lines().findFirst().orElse(question).strip();
        } catch (RuntimeException ex) {
            return question;
        }
    }

    /**
     * Finds the clauses for a question, only in this user's chosen policy, with vector and keyword
     * search. A follow-up is also searched together with the previous question. A coverage
     * question always sees the policy's exclusions.
     */
    private List<Document> retrieve(String question, List<ChatMessage> history, AppUser user, UUID policyId) {
        String contextual = PromptBuilder.contextualQuery(history, question);
        List<String> queries = contextual.equals(question) ? List.of(question) : List.of(question, contextual);
        List<Document> found = new ArrayList<>(
                search.search(queries, user.getId(), policyId, retrieval.topK(), retrieval.topK()));
        for (Document exclusion : search.exclusions(question, user.getId(), policyId, EXCLUSION_CLAUSES)) {
            if (found.stream().noneMatch(d -> d.getId().equals(exclusion.getId()))) {
                found.add(exclusion);
            }
        }
        return found;
    }

    /** Returns only the excerpts the model actually cited, keeping the model's numbering. */
    private static List<Citation> citationsUsed(String answer, List<Document> sources) {
        Set<Integer> cited = PromptBuilder.citedIndices(answer);
        List<Citation> citations = new ArrayList<>();
        for (int index : cited) {
            if (index >= 1 && index <= sources.size()) {
                citations.add(citation(index, sources.get(index - 1)));
            }
        }
        return citations;
    }

    private static List<String> citedTexts(String answer, List<Document> sources) {
        List<String> texts = new ArrayList<>();
        for (int index : PromptBuilder.citedIndices(answer)) {
            if (index >= 1 && index <= sources.size()) {
                texts.add(sources.get(index - 1).getText());
            }
        }
        return texts;
    }

    private static List<Citation> asCitations(List<Document> sources) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            citations.add(citation(i + 1, sources.get(i)));
        }
        return citations;
    }

    private static Citation citation(int index, Document source) {
        return new Citation(
                index,
                String.valueOf(source.getMetadata().get(ProcessingService.META_DOCUMENT_ID)),
                String.valueOf(source.getMetadata().get(ProcessingService.META_FILE_NAME)),
                toInteger(source.getMetadata().get(ProcessingService.META_PAGE)),
                toText(source.getMetadata().get(ProcessingService.META_SECTION)),
                snippet(source.getText()),
                source.getScore());
    }

    /** The passage as one line, without its Markdown heading (the section name is shown separately). */
    static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("(?m)^#{1,6}\\s+.*$", "").strip().replaceAll("\\s+", " ");
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String toText(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
