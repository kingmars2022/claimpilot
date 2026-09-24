package com.companybrain.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.companybrain.config.AppProperties;
import com.companybrain.document.IndexingService;

/**
 * Retrieval-augmented question answering: find the most relevant chunks, then let the model
 * answer from those chunks only, citing each one as [n].
 */
@Service
public class ChatService {

    private static final int SNIPPET_LENGTH = 320;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptBuilder promptBuilder;
    private final AppProperties.Retrieval retrieval;

    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, PromptBuilder promptBuilder,
                       AppProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.promptBuilder = promptBuilder;
        this.retrieval = properties.retrieval();
    }

    public ChatAnswer ask(ChatRequest request) {
        long start = System.nanoTime();
        List<Document> sources = vectorStore.similaritySearch(SearchRequest.builder()
                .query(request.question())
                .topK(retrieval.topK())
                .similarityThreshold(retrieval.similarityThreshold())
                .build());

        // Nothing relevant: answer honestly without spending a model call.
        if (sources.isEmpty()) {
            return new ChatAnswer(PromptBuilder.NO_ANSWER, false, List.of(), elapsedMs(start));
        }

        String answer = PromptBuilder.clean(chatClient.prompt()
                .system(promptBuilder.systemPrompt())
                .user(promptBuilder.userPrompt(request.question(), sources))
                .call()
                .content());

        List<Citation> citations = citationsUsed(answer, sources);
        if (answer.isBlank()) {
            answer = PromptBuilder.NO_ANSWER;
        }
        return new ChatAnswer(answer, !citations.isEmpty(), citations, elapsedMs(start));
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
                    snippet(source.getText()),
                    source.getScore()));
        }
        return citations;
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH) + "…";
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
