package com.companybrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.companybrain.chat.ChatAnswer;
import com.companybrain.chat.ChatRequest;
import com.companybrain.chat.ChatService;
import com.companybrain.chat.Citation;
import com.companybrain.chat.PromptBuilder;
import com.companybrain.document.DocumentResponse;
import com.companybrain.document.DocumentService;
import com.companybrain.document.DocumentStatus;

/**
 * Runs the whole phase 1 flow (upload, background indexing, retrieval, answer, delete) against a
 * real pgvector database. The AI models are replaced by fakes so the test needs no Ollama.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none"
})
class RagFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("companybrain.storage.local-root", uploads::toString);
    }

    private static final String HANDBOOK = """
            # Employee Handbook

            ## Vacation
            Full-time employees receive 15 paid vacation days in their first year of service.
            Vacation requests must be submitted in PeopleHub at least 10 business days in advance.

            ## Sick days
            Employees have 7 paid sick days per calendar year.
            """;

    @Autowired
    DocumentService documentService;

    @Autowired
    ChatService chatService;

    @Autowired
    FakeChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.prompts.clear();
    }

    @Test
    void answersFromUploadedDocumentWithCitation() throws Exception {
        DocumentResponse doc = upload("handbook.md", HANDBOOK);
        // One chunk per Markdown section: "Vacation" and "Sick days".
        assertThat(waitUntilIndexed(doc.id()).chunkCount()).isEqualTo(2);

        ChatAnswer answer = chatService.ask(new ChatRequest("How many vacation days do I get in my first year?"));

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.answer()).contains("[1]");
        assertThat(answer.citations()).hasSize(1);
        Citation citation = answer.citations().getFirst();
        assertThat(citation.fileName()).isEqualTo("handbook.md");
        assertThat(citation.section()).isEqualTo("Vacation");
        assertThat(citation.snippet()).startsWith("Full-time employees receive 15 paid vacation days");
        assertThat(chatModel.prompts).singleElement().asString().contains("section: Vacation");
        // The retrieved passage was actually handed to the model.
        assertThat(chatModel.prompts).singleElement().asString().contains("15 paid vacation days");

        documentService.delete(doc.id());
    }

    @Test
    void unrelatedQuestionReturnsFixedTextWithoutCallingModel() throws Exception {
        DocumentResponse doc = upload("handbook.md", HANDBOOK);
        waitUntilIndexed(doc.id());

        ChatAnswer answer = chatService.ask(new ChatRequest("Which espresso machine is on floor seven?"));

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.answer()).isEqualTo(PromptBuilder.NO_ANSWER);
        assertThat(chatModel.prompts).isEmpty();

        documentService.delete(doc.id());
    }

    @Test
    void deletedDocumentIsNoLongerSearched() throws Exception {
        DocumentResponse doc = upload("handbook.md", HANDBOOK);
        waitUntilIndexed(doc.id());

        documentService.delete(doc.id());

        ChatAnswer answer = chatService.ask(new ChatRequest("How many vacation days do I get in my first year?"));
        assertThat(answer.answer()).isEqualTo(PromptBuilder.NO_ANSWER);
        assertThat(documentService.list()).noneMatch(d -> d.id().equals(doc.id()));
    }

    @Test
    void rejectsUnsupportedFileType() {
        assertThatThrownBy(() -> upload("tool.exe", "binary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    private DocumentResponse upload(String fileName, String content) throws Exception {
        return documentService.upload(new MockMultipartFile(
                "file", fileName, "text/markdown", content.getBytes(StandardCharsets.UTF_8)));
    }

    private DocumentResponse waitUntilIndexed(UUID id) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            DocumentResponse doc = documentService.get(id);
            if (doc.status() == DocumentStatus.FAILED) {
                throw new AssertionError("Indexing failed: " + doc.errorMessage());
            }
            if (doc.status() == DocumentStatus.INDEXED) {
                return doc;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document " + id + " was not indexed in time");
    }

    @TestConfiguration
    static class FakeModels {

        @Bean
        EmbeddingModel embeddingModel() {
            return new HashingEmbeddingModel();
        }

        @Bean
        FakeChatModel chatModel() {
            return new FakeChatModel();
        }
    }

    /** Answers with a fixed sentence citing source [1] and records every prompt it receives. */
    static class FakeChatModel implements ChatModel {

        final List<String> prompts = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "Full-time employees receive 15 paid vacation days in their first year [1]."))));
        }
    }

    /**
     * Deterministic stand-in for bge-m3: a normalized bag of hashed words in 1024 dimensions.
     * Texts that share words get a high cosine similarity; unrelated texts score near zero.
     */
    static class HashingEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 1024;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.length() > 3) {
                    v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            float scale = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < DIMENSIONS; i++) {
                v[i] *= scale;
            }
            return v;
        }
    }
}
