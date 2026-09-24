package com.companybrain.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application over HTTP (MockMvc) against real pgvector and MongoDB containers.
 * The AI models are replaced by fakes, so the tests need Docker but not Ollama.
 * <p>
 * The containers are started once and shared by every test class, which also share one Spring
 * context. Demo users come from the V3 Flyway migration; all use the password "demo1234".
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "companybrain.retrieval.similarity-threshold=0.25"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FakeModels.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "demo1234";

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    private static final Path UPLOADS;

    static {
        postgres.start();
        mongo.start();
        try {
            UPLOADS = Files.createTempDirectory("companybrain-uploads");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("companybrain.storage.local-root", UPLOADS::toString);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.reset();
    }

    // ---------- HTTP helpers ----------

    protected String login(String username) throws Exception {
        return login(username, PASSWORD);
    }

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(String token, B request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Uploads a Markdown file and waits until it is indexed; returns the document id. */
    protected String uploadIndexed(String token, String fileName, String content, Long... departmentIds)
            throws Exception {
        var request = multipart("/api/documents").file(new MockMultipartFile(
                "file", fileName, "text/markdown", content.getBytes(StandardCharsets.UTF_8)));
        for (Long id : departmentIds) {
            request.param("departmentIds", id.toString());
        }
        String body = mvc.perform(as(token, request))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        waitUntilIndexed(token, id);
        return id;
    }

    protected void waitUntilIndexed(String token, String documentId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            String body = mvc.perform(as(token, get("/api/documents/" + documentId)))
                    .andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if ("FAILED".equals(status)) {
                throw new AssertionError("Indexing failed: " + JsonPath.read(body, "$.errorMessage"));
            }
            if ("INDEXED".equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document " + documentId + " was not indexed in time");
    }

    /** Asks a question and returns the raw JSON answer. */
    protected String ask(String token, String question, String conversationId) throws Exception {
        Map<String, Object> body = conversationId == null
                ? Map.of("question", question)
                : Map.of("question", question, "conversationId", conversationId);
        return mvc.perform(as(token, post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(json(body))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected Long departmentId(String token, String code) throws Exception {
        String body = mvc.perform(as(token, get("/api/departments")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.code == '" + code + "')].id");
        return ids.getFirst().longValue();
    }

    /** Minimal JSON writer for flat test request bodies. */
    protected static String json(Map<String, ?> values) {
        List<String> fields = new ArrayList<>();
        values.forEach((key, value) -> fields.add("\"" + key + "\":" + jsonValue(value)));
        return "{" + String.join(",", fields) + "}";
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(IntegrationTestBase::jsonValue).toList()) + "]";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- Fake AI models ----------

    @TestConfiguration
    public static class FakeModels {

        @Bean
        EmbeddingModel embeddingModel() {
            return new HashingEmbeddingModel();
        }

        @Bean
        FakeChatModel chatModel() {
            return new FakeChatModel();
        }
    }

    /**
     * Records every prompt. Answers cite source [1]; follow-up rewrites return {@link #rewriteTo}.
     */
    public static class FakeChatModel implements ChatModel {

        public final List<String> answerPrompts = new CopyOnWriteArrayList<>();
        public final List<String> rewritePrompts = new CopyOnWriteArrayList<>();
        public volatile String rewriteTo = "";

        void reset() {
            answerPrompts.clear();
            rewritePrompts.clear();
            rewriteTo = "";
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String contents = prompt.getContents();
            String reply;
            if (contents.contains("Follow-up question:")) {
                rewritePrompts.add(contents);
                reply = rewriteTo;
            } else {
                answerPrompts.add(contents);
                reply = "Here is what the policy says [1].";
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
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
