package com.companybrain.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import com.companybrain.config.AppProperties;
import com.companybrain.conversation.ChatMessage;
import com.companybrain.conversation.Conversation;
import com.companybrain.conversation.ConversationService;
import com.companybrain.document.DocumentAccess;
import com.companybrain.document.IndexingService;
import com.companybrain.user.AppUser;

/**
 * Retrieval-augmented question answering: find the most relevant chunks the user may see, then
 * let the model answer from those chunks only, citing each one as [n]. Every exchange is saved to
 * the user's conversation in MongoDB.
 */
@Service
public class ChatService {

    private static final int SNIPPET_LENGTH = 320;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversations;
    private final AppProperties.Retrieval retrieval;
    private final int historyTurns;

    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, PromptBuilder promptBuilder,
                       ConversationService conversations, AppProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.promptBuilder = promptBuilder;
        this.conversations = conversations;
        this.retrieval = properties.retrieval();
        this.historyTurns = properties.conversation().historyTurns();
    }

    /**
     * Answers a question for one user. Retrieval only sees chunks the user may access, and a
     * follow-up in an existing conversation is answered with the earlier turns as context.
     */
    public ChatAnswer ask(ChatRequest request, AppUser user) {
        long start = System.nanoTime();
        String question = request.question().strip();
        Conversation conversation = request.conversationId() == null || request.conversationId().isBlank()
                ? Conversation.start(user.getUsername(), question)
                : conversations.getOwned(request.conversationId(), user.getUsername());

        List<ChatMessage> history = conversation.recentMessages(historyTurns);
        List<Document> sources = retrieve(question, history, user);

        String answer;
        List<Citation> citations;
        if (sources.isEmpty()) {
            // Nothing relevant (or nothing this user may see): answer honestly without a model call.
            answer = PromptBuilder.NO_ANSWER;
            citations = List.of();
        } else {
            answer = PromptBuilder.clean(chatClient.prompt()
                    .system(promptBuilder.systemPrompt())
                    .user(promptBuilder.userPrompt(question, sources, history))
                    .call()
                    .content());
            citations = citationsUsed(answer, sources);
            if (answer.isBlank()) {
                answer = PromptBuilder.NO_ANSWER;
            }
        }

        boolean grounded = !citations.isEmpty();
        conversation.append(ChatMessage.question(question), ChatMessage.answer(answer, grounded, citations));
        Conversation saved = conversations.save(conversation);
        return new ChatAnswer(answer, grounded, citations, elapsedMs(start), saved.getId());
    }

    /**
     * Finds the passages for a question. A follow-up such as "And from the third year?" is also
     * searched together with the previous question, and the two result lists are merged by
     * score. This costs one extra embedding lookup (milliseconds) instead of an extra model call
     * to rewrite the question (seconds on a local model). Searching the question on its own as
     * well keeps a change of topic within the same conversation working.
     */
    private List<Document> retrieve(String question, List<ChatMessage> history, AppUser user) {
        List<Document> direct = vectorStore.similaritySearch(searchRequest(question, user));
        String contextual = PromptBuilder.contextualQuery(history, question);
        if (contextual.equals(question)) {
            return direct;
        }
        List<Document> withContext = vectorStore.similaritySearch(searchRequest(contextual, user));
        return mergeByScore(direct, withContext, retrieval.topK());
    }

    /** Keeps each chunk once with its best score, highest first, at most {@code limit}. */
    static List<Document> mergeByScore(List<Document> first, List<Document> second, int limit) {
        Map<String, Document> best = new LinkedHashMap<>();
        for (Document doc : concat(first, second)) {
            best.merge(doc.getId(), doc, (a, b) -> score(b) > score(a) ? b : a);
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(ChatService::score).reversed())
                .limit(limit)
                .toList();
    }

    private static List<Document> concat(List<Document> a, List<Document> b) {
        List<Document> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static double score(Document doc) {
        return doc.getScore() == null ? 0 : doc.getScore();
    }

    private SearchRequest searchRequest(String query, AppUser user) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(retrieval.topK())
                .similarityThreshold(retrieval.similarityThreshold());
        Filter.Expression filter = DocumentAccess.filterFor(user);
        if (filter != null) {
            builder.filterExpression(filter);
        }
        return builder.build();
    }

    /** Returns only the sources the model actually cited, keeping the model's numbering. */
    private static List<Citation> citationsUsed(String answer, List<Document> sources) {
        Set<Integer> cited = PromptBuilder.citedIndices(answer);
        List<Citation> citations = new ArrayList<>();
        for (int index : cited) {
            if (index < 1 || index > sources.size()) {
                continue;
            }
            Document source = sources.get(index - 1);
            citations.add(new Citation(
                    index,
                    String.valueOf(source.getMetadata().get(IndexingService.META_DOCUMENT_ID)),
                    String.valueOf(source.getMetadata().get(IndexingService.META_FILE_NAME)),
                    toInteger(source.getMetadata().get(IndexingService.META_PAGE)),
                    toText(source.getMetadata().get(IndexingService.META_SECTION)),
                    snippet(source.getText()),
                    source.getScore()));
        }
        return citations;
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
